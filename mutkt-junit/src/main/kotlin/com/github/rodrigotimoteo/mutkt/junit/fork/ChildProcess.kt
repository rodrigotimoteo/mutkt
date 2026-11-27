package com.github.rodrigotimoteo.mutkt.junit.fork

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.nanoseconds

private const val STDERR_TAIL_BYTES = 4096
private const val READY_WAIT_MILLIS = 60_000L
private const val EXIT_WAIT_MILLIS = 2_000L
private const val KILL_WAIT_MILLIS = 5_000L

// A POSIX shell convention the JVM follows: a process ended by signal N exits with 128 + N.
private const val SIGNAL_EXIT_BASE = 128

/** What a kill by an [OutOfMemoryError] that ended the child names as the killing test. */
internal const val OUT_OF_MEMORY: String = "OutOfMemoryError"

/** One forked JVM speaking the frame protocol; used by one thread at a time. */
internal class ChildProcess private constructor(
    val process: Process,
    private val tmpDir: Path?,
) : Closeable {
    private val stderr = StderrTail(STDERR_TAIL_BYTES)
    private val output = DataOutputStream(process.outputStream.buffered())
    private val replies = LinkedBlockingQueue<Event>()
    private val stderrDrain = thread(isDaemon = true, name = "mutkt-fork-stderr") { drainStderr() }

    init {
        thread(
            isDaemon = true,
            name = "mutkt-fork-reader",
        ) { readReplies(DataInputStream(process.inputStream.buffered())) }
    }

    val isAlive: Boolean get() = process.isAlive

    /** True once a result asked not to reuse this child: its memory is past the limits. */
    @Volatile var retiring: Boolean = false
        private set

    /**
     * Sends [request]. Silence for [prepareMillis] before the child reports Started throws [PreparationStalled] (a
     * stuck child is not a detection); silence for [timeoutMillis] + [graceMillis] after it is a [Outcome.Timeout] of
     * [timeoutMillis].
     * A child that dies before Started throws; one that dies after it was killed by the mutant ([Outcome.Killed]).
     */
    fun exchange(
        request: ForkRequest,
        context: String,
        timeoutMillis: Long,
        graceMillis: Long,
        prepareMillis: Long,
    ): ForkReply.Result =
        try {
            writeRequest(output, request)
            // A mutant or test class that cannot link answers Result without a Started.
            val first =
                awaitReply(prepareMillis)
                    ?: throw PreparationStalled(
                        failure(
                            "$context, no Started within $prepareMillis ms",
                            IOException("Silent while preparing"),
                        ),
                        stderr.shown(),
                    )
            if (first !is ForkReply.Started) {
                first.asResult()
            } else {
                val start = System.nanoTime()
                try {
                    awaitReply(timeoutMillis + graceMillis)?.asResult()
                        ?: ForkReply.Result(Outcome.Timeout, timeoutMillis)
                } catch (e: IOException) {
                    exitedWhileTesting(request, context, e, start)
                }
            }
        } catch (e: IOException) {
            throw failure(context, e)
        } catch (e: ForkProtocolException) {
            throw failure(context, e)
        }

    // System.exit or an OutOfMemoryError escaping JUnit while the tests run: the mutant did it.
    // A signal (the OS OOM killer, a user) usually is not the mutant's doing.
    private fun exitedWhileTesting(
        request: ForkRequest,
        context: String,
        cause: IOException,
        start: Long,
    ): ForkReply.Result {
        val code = awaitExit()
        if (code == null || code > SIGNAL_EXIT_BASE) throw failure(context, cause, code)
        check(request !is ForkRequest.Baseline) {
            "the test JVM exited with code $code on the original classes. Last stderr: ${stderr.shown()}"
        }
        return ForkReply.Result(
            Outcome.Killed(killedBy(code)),
            (System.nanoTime() - start).nanoseconds.inWholeMilliseconds,
        )
    }

    fun requestShutdown() {
        try {
            writeRequest(output, ForkRequest.Shutdown)
        } catch (_: IOException) {
            // Already gone; kill() finishes the job.
        }
    }

    /** Idempotent: destroys the process tree, waits for it, releases the pipes and deletes its temp directory. */
    fun kill() {
        process.destroyTree()
        close()
        // Never under a live child; one that outlived the kill wait is left to the executor's close.
        if (!process.isAlive) tmpDir?.toFile()?.deleteRecursively()
    }

    override fun close() {
        listOf<Closeable>(output, process.inputStream, process.errorStream).forEach {
            try {
                it.close()
            } catch (_: IOException) {
                // Nothing left to release.
            }
        }
    }

    private fun awaitReply(timeoutMillis: Long): ForkReply? =
        when (val event = replies.poll(timeoutMillis, TimeUnit.MILLISECONDS)) {
            null -> null
            is Event.Reply -> event.reply
            is Event.Closed -> throw event.cause ?: IOException("Child closed its output")
        }

    // The reader runs until the child's stdout ends, so a reply is never lost between two waits.
    private fun readReplies(input: DataInputStream) {
        try {
            while (true) {
                val reply = readReply(input)
                if (reply == null) {
                    replies.put(Event.Closed(null))
                    return
                }
                if (reply is ForkReply.Result && reply.retire) retiring = true
                replies.put(Event.Reply(reply))
            }
        } catch (e: IOException) {
            replies.put(Event.Closed(e))
        } catch (e: ForkProtocolException) {
            replies.put(Event.Closed(e))
        }
    }

    private fun drainStderr() {
        val buffer = ByteArray(STDERR_TAIL_BYTES)
        try {
            while (true) {
                val read = process.errorStream.read(buffer)
                if (read < 0) return
                stderr.append(buffer, read)
            }
        } catch (_: IOException) {
            // Stream closed by kill().
        }
    }

    // [exitCode] null: the child was still running and is now killed.
    private fun failure(
        context: String,
        cause: Throwable,
        exitCode: Int? = awaitExit(),
    ): IllegalStateException {
        val state =
            when {
                exitCode == null -> "still running, killed"
                exitCode > SIGNAL_EXIT_BASE -> "killed by signal ${exitCode - SIGNAL_EXIT_BASE}"
                else -> "exit code $exitCode"
            }
        return IllegalStateException(
            "Forked test JVM failed ($state) while $context. Last stderr: ${stderr.shown()}",
            cause,
        )
    }

    // The child may still be exiting (System.exit in a mutant); give it a moment so the exit code is real.
    // Returns the exit code, or null when it did not exit and was killed; either way the pipes are released.
    private fun awaitExit(): Int? {
        val exited = process.waitFor(EXIT_WAIT_MILLIS, TimeUnit.MILLISECONDS)
        if (!exited) process.destroyTree()
        stderrDrain.join(EXIT_WAIT_MILLIS)
        close()
        return if (exited) process.exitValue() else null
    }

    private sealed interface Event {
        data class Reply(
            val reply: ForkReply,
        ) : Event

        data class Closed(
            val cause: Exception?,
        ) : Event
    }

    companion object {
        /**
         * Starts the JVM and completes the handshake; fails with the exit code and stderr when it cannot.
         * The child owns [tmpDir] and deletes it once it has ended.
         */
        fun start(
            command: List<String>,
            environment: Map<String, String>,
            workingDir: Path?,
            tmpDir: Path?,
        ): ChildProcess {
            val child = spawn(command, environment, workingDir, tmpDir)
            var ready = false
            try {
                child.handshake()
                ready = true
            } catch (e: IOException) {
                throw child.failure("starting", e)
            } catch (e: ForkProtocolException) {
                throw child.failure("starting", e)
            } finally {
                // Whatever ends the start, an interrupt (a cancelled build) too, must not leave a child behind.
                if (!ready) child.kill()
            }
            return child
        }

        private fun ChildProcess.handshake() {
            val ready = awaitReply(READY_WAIT_MILLIS) ?: throw IOException("No Ready within $READY_WAIT_MILLIS ms")
            if (ready !is ForkReply.Ready) throw ForkProtocolException("Expected Ready, got $ready")
            ready.requireCompatible()
        }

        private fun spawn(
            command: List<String>,
            environment: Map<String, String>,
            workingDir: Path?,
            tmpDir: Path?,
        ): ChildProcess {
            val builder = ProcessBuilder(command)
            builder.environment().putAll(environment)
            builder.directory(workingDir?.toFile())
            return try {
                ChildProcess(builder.start(), tmpDir)
            } catch (e: IOException) {
                tmpDir?.toFile()?.deleteRecursively()
                throw IllegalStateException("Cannot start forked test JVM ${command.first()}: ${e.message}", e)
            }
        }
    }
}

private fun ForkReply.asResult(): ForkReply.Result =
    this as? ForkReply.Result ?: throw ForkProtocolException("Unexpected reply $this")

private fun killedBy(exitCode: Int): String =
    if (exitCode == EXIT_OUT_OF_MEMORY) OUT_OF_MEMORY else "JVM exit $exitCode"

// Descendants first: a mutant or test may have forked helpers that would otherwise outlive the child.
private fun Process.destroyTree() {
    descendants().forEach { it.destroyForcibly() }
    destroyForcibly().waitFor(KILL_WAIT_MILLIS, TimeUnit.MILLISECONDS)
}
