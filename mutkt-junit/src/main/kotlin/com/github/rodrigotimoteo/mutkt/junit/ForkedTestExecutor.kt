package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.TestExecutor
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.junit.fork.ChildProcess
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration

private const val FORK_MAIN = "com.github.rodrigotimoteo.mutkt.junit.fork.ForkMain"
private const val MAX_START_FAILURES = 3
private const val SHUTDOWN_WAIT_NANOS = 5_000_000_000L
private const val CLOSED = "ForkedTestExecutor is closed"

/**
 * Runs mutants in a pool of child JVMs (ADR 0008), at most `parallelism` at a time; each child serves one mutant
 * at a time and is reused. A child that times out is killed and replaced, which is what stops an endless loop.
 * A crashed child, or one that cannot start, makes [run] throw with its exit code and last stderr lines.
 */
public class ForkedTestExecutor(
    private val settings: ForkSettings,
) : TestExecutor,
    AutoCloseable {
    private val permits = Semaphore(settings.parallelism)
    private val idle = ConcurrentLinkedDeque<ChildProcess>()
    private val children = CopyOnWriteArrayList<ChildProcess>()
    private val started = AtomicInteger()
    private val failedStarts = AtomicInteger()
    private val lastStartFailure = AtomicReference<String>()
    private val startup = lazy { writeStartupFile() }
    private val lock = Any()

    @Volatile private var closed = false

    internal val startedChildren: Int get() = started.get()
    internal val liveProcesses: List<Process> get() = children.map { it.process }.filter { it.isAlive }

    override fun run(
        mutant: Mutant,
        bytes: ByteArray,
        deadline: Duration,
    ): Outcome {
        require(deadline.isPositive()) { "Deadline must be positive, was $deadline" }
        check(!closed) { CLOSED }
        permits.acquire()
        try {
            check(!closed) { CLOSED }
            return runOnChild(borrow(), mutant, bytes, deadline)
        } catch (e: IllegalStateException) {
            // close() kills busy children, which looks like a crash from here.
            if (closed) throw IllegalStateException(CLOSED, e)
            throw e
        } finally {
            permits.release()
        }
    }

    private fun runOnChild(
        child: ChildProcess,
        mutant: Mutant,
        bytes: ByteArray,
        deadline: Duration,
    ): Outcome {
        var reusable = false
        try {
            val outcome = child.run(mutant, bytes, deadline.inWholeMilliseconds.coerceAtLeast(1), settings.graceMillis)
            // A child that timed out may still be running the abandoned mutant thread.
            reusable = outcome != Outcome.Timeout
            return outcome
        } finally {
            if (reusable) idle.push(child)
            // A close() that raced with the push may already have taken its snapshot.
            if (!reusable || closed) discard(child)
        }
    }

    // Only idle children get a Shutdown frame: a busy child's stream belongs to its running thread, so it is killed.
    override fun close() {
        synchronized(lock) { closed = true }
        val pooled = generateSequence { idle.poll() }.toList()
        pooled.forEach(ChildProcess::requestShutdown)
        val limit = System.nanoTime() + SHUTDOWN_WAIT_NANOS
        pooled.forEach { it.process.waitFor(maxOf(0, limit - System.nanoTime()), TimeUnit.NANOSECONDS) }
        children.toList().forEach(::discard)
        synchronized(lock) {
            if (startup.isInitialized()) {
                startup.value.parent
                    .toFile()
                    .deleteRecursively()
            }
        }
    }

    private fun borrow(): ChildProcess {
        while (true) {
            val candidate = idle.poll() ?: return start()
            if (candidate.isAlive) return candidate
            discard(candidate)
        }
    }

    private fun start(): ChildProcess {
        check(!closed) { CLOSED }
        check(failedStarts.get() < MAX_START_FAILURES) {
            "Forked test JVM failed to start $MAX_START_FAILURES times in a row, giving up. " +
                "Last failure: ${lastStartFailure.get()}"
        }
        val child =
            try {
                ChildProcess.start(command(), settings.environment, settings.workingDir)
            } catch (e: IllegalStateException) {
                failedStarts.incrementAndGet()
                lastStartFailure.set(e.message)
                throw e
            }
        failedStarts.set(0)
        started.incrementAndGet()
        children.add(child)
        if (closed) {
            discard(child)
            error(CLOSED)
        }
        return child
    }

    private fun discard(child: ChildProcess) {
        children.remove(child)
        idle.remove(child)
        child.kill()
    }

    // Under the lock so a close() cannot delete the startup directory just before it is (re)created.
    private fun command(): List<String> =
        synchronized(lock) {
            check(!closed) { CLOSED }
            listOf(settings.javaExecutable.toString()) +
                settings.jvmArgs +
                listOf("-cp", classpath.joinToString(File.pathSeparator), FORK_MAIN, startup.value.toString())
        }

    private val classpath by lazy {
        engineClasspathFor(settings.engineClasspath ?: derivedEngineClasspath(), settings.classpath)
    }

    // A file, not arguments: the classpath can exceed the OS command-line limit.
    private fun writeStartupFile(): Path {
        val lines =
            listOf("projectClassDir=${settings.projectClassDir}", "testClassDir=${settings.testClassDir}") +
                settings.classpath.map { "classpath=$it" } +
                settings.engines.map { "engine=$it" } +
                settings.includeTags.map { "includeTag=$it" } +
                settings.excludeTags.map { "excludeTag=$it" }
        return Files.write(Files.createTempDirectory("mutkt-fork").resolve("startup.properties"), lines)
    }
}
