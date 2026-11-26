package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.TestExecutor
import com.github.rodrigotimoteo.mutkt.application.port.TimedOutcome
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.junit.fork.ChildProcess
import com.github.rodrigotimoteo.mutkt.junit.fork.ForkRequest
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipFile
import kotlin.time.Duration

private const val FORK_MAIN = "com.github.rodrigotimoteo.mutkt.junit.fork.ForkMain"
private const val MAX_START_FAILURES = 3
private const val SHUTDOWN_WAIT_NANOS = 5_000_000_000L
private const val CLOSED = "ForkedTestExecutor is closed"
private const val TMPDIR = "java.io.tmpdir"
private const val ROBOLECTRIC_MARKER = "org/robolectric/internal/SandboxManager.class"

/**
 * Runs mutants in a pool of child JVMs (ADR 0008), at most `parallelism` at a time; each child serves one mutant
 * at a time and is reused. A child that times out is killed and replaced, which is what stops an endless loop.
 * A child that dies once the tests started is a kill by the mutant and is replaced (ADR 0002); one that dies
 * before, or cannot start, makes [run] throw with its exit code and last stderr lines.
 * A child whose memory is past its limits after a run (a run's class loader stays reachable while a library thread or
 * static holds it) is replaced too. With Robolectric on the classpath no child is reused: it caches its sandbox and the
 * instrumented project classes in statics with no reset API, and that a fresh class loader per run resets all of them
 * is not shown on a real Robolectric project.
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
    private val tmpDirs = AtomicInteger()
    private val startup = lazy { writeStartupFile() }

    // Checked here so a missing engine fails before any child JVM starts.
    private val projectClasspath = projectClasspathFor(settings.engines, settings.classpath)
    private val lock = Any()

    @Volatile private var closed = false

    internal val startedChildren: Int get() = started.get()
    internal val liveProcesses: List<Process> get() = children.map { it.process }.filter { it.isAlive }

    override fun run(
        mutant: Mutant,
        bytes: ByteArray,
        deadline: Duration,
    ): Outcome = onChild(deadline, "running a mutant") { ForkRequest.Run(mutant, bytes, it) }.outcome

    override fun baseline(deadline: Duration): TimedOutcome =
        onChild(deadline, "running the baseline") { ForkRequest.Baseline(it) }

    private fun onChild(
        deadline: Duration,
        context: String,
        request: (timeoutMillis: Long) -> ForkRequest,
    ): TimedOutcome {
        require(deadline.isPositive()) { "Deadline must be positive, was $deadline" }
        check(!closed) { CLOSED }
        val timeoutMillis = deadline.inWholeMilliseconds.coerceAtLeast(1)
        permits.acquire()
        try {
            check(!closed) { CLOSED }
            val outcome = runOnChild(borrow(), request(timeoutMillis), context, timeoutMillis)
            // A child that close() killed mid-run looks like a kill by the mutant.
            check(!closed) { CLOSED }
            return outcome
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
        request: ForkRequest,
        context: String,
        timeoutMillis: Long,
    ): TimedOutcome {
        var reusable = false
        try {
            val outcome = child.exchange(request, context, timeoutMillis, settings.graceMillis, settings.prepareMillis)
            // A child that timed out may still be running the abandoned mutant thread; one that died is replaced.
            reusable = reuseChildren && outcome.outcome != Outcome.Timeout && child.isAlive && !child.retiring
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
        try {
            val pooled = generateSequence { idle.poll() }.toList()
            pooled.forEach(ChildProcess::requestShutdown)
            val limit = System.nanoTime() + SHUTDOWN_WAIT_NANOS
            pooled.forEach { it.process.waitFor(maxOf(0, limit - System.nanoTime()), TimeUnit.NANOSECONDS) }
            children.toList().forEach(::discard)
        } finally {
            synchronized(lock) {
                if (startup.isInitialized()) {
                    startup.value.parent
                        .toFile()
                        .deleteRecursively()
                }
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
        val (command, tmpDir) = command()
        val child =
            try {
                ChildProcess.start(command, settings.environment, settings.workingDir, tmpDir)
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
    // Each child gets its own java.io.tmpdir: libraries (Robolectric natives, MockK, JUnit) leave files there that
    // only their shutdown hooks remove, and a killed child runs none; the child deletes it once it is gone.
    private fun command(): Pair<List<String>, Path?> =
        synchronized(lock) {
            check(!closed) { CLOSED }
            val tmpDir =
                if (userTmpDir) {
                    null
                } else {
                    Files.createDirectory(
                        startup.value.resolveSibling("tmp-${tmpDirs.incrementAndGet()}"),
                    )
                }
            val command =
                listOf(settings.javaExecutable.toString()) +
                    settings.jvmArgs +
                    kotestArgs +
                    listOfNotNull(tmpDir?.let { "-D$TMPDIR=$it" }) +
                    listOf("-cp", classpath.joinToString(File.pathSeparator), FORK_MAIN, startup.value.toString())
            command to tmpDir
        }

    private val userTmpDir = settings.jvmArgs.any { it.startsWith("-D$TMPDIR=") }

    private val reuseChildren by lazy { settings.classpath.none(::hasRobolectric) }

    private val kotestArgs by lazy { kotestProperties(projectClasspath, settings.testClassDir, settings.jvmArgs) }

    private val classpath by lazy {
        engineClasspathFor(settings.engineClasspath ?: derivedEngineClasspath(), settings.classpath)
    }

    // A file, not arguments: the classpath can exceed the OS command-line limit.
    private fun writeStartupFile(): Path {
        val lines =
            listOf("projectClassDir=${settings.projectClassDir}", "testClassDir=${settings.testClassDir}") +
                projectClasspath.map { "classpath=$it" } +
                settings.engines.map { "engine=$it" } +
                settings.includeTags.map { "includeTag=$it" } +
                settings.excludeTags.map { "excludeTag=$it" }
        return Files.write(Files.createTempDirectory("mutkt-fork").resolve("startup.properties"), lines)
    }
}

// Only jars and directories can hold classes; other entries (a missing directory, a stray file) are skipped.
private fun hasRobolectric(entry: Path): Boolean =
    when {
        Files.isDirectory(entry) -> Files.isRegularFile(entry.resolve(ROBOLECTRIC_MARKER))
        Files.isRegularFile(entry) && entry.toString().endsWith(".jar") ->
            ZipFile(entry.toFile()).use { it.getEntry(ROBOLECTRIC_MARKER) != null }
        else -> false
    }
