package com.github.rodrigotimoteo.mutkt.junit.fork

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private const val STDERR_TAIL_BYTES = 4096
private const val READY_WAIT_MILLIS = 60_000L
private const val EXIT_WAIT_MILLIS = 2_000L
private const val KILL_WAIT_MILLIS = 5_000L

/** One forked JVM speaking the frame protocol; used by one thread at a time. */
internal class ChildProcess private constructor(
    val process: Process,
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

    /**
     * Runs one mutant. Silence for [prepareMillis] before the child reports Started throws (a stuck child is not a
     * detection); silence for [timeoutMillis] + [graceMillis] after it is a [Outcome.Timeout].
     */
    fun run(
        mutant: Mutant,
        bytes: ByteArray,
        timeoutMillis: Long,
        graceMillis: Long,
        prepareMillis: Long,
    ): Outcome =
        try {
            writeRequest(output, ForkRequest.Run(mutant, bytes, timeoutMillis))
            // A mutant or test class that cannot link answers Result without a Started.
            val first =
                awaitReply(prepareMillis)
                    ?: throw failure(
                        "preparing ${mutant.id}, no Started within $prepareMillis ms",
                        IOException("Silent while preparing"),
                    )
            val reply = if (first is ForkReply.Started) awaitReply(timeoutMillis + graceMillis) else first
            when (reply) {
                null -> Outcome.Timeout
                is ForkReply.Result -> reply.outcome
                else -> throw ForkProtocolException("Unexpected reply $reply")
            }
        } catch (e: IOException) {
            throw failure("running ${mutant.id}", e)
        } catch (e: ForkProtocolException) {
            throw failure("running ${mutant.id}", e)
        }

    fun requestShutdown() {
        try {
            writeRequest(output, ForkRequest.Shutdown)
        } catch (_: IOException) {
            // Already gone; kill() finishes the job.
        }
    }

    /** Idempotent: destroys the process tree, waits for it and releases the pipes. */
    fun kill() {
        destroy()
        close()
    }

    // Descendants first: a mutant or test may have forked helpers that would otherwise outlive the child.
    private fun destroy() {
        process.descendants().forEach { it.destroyForcibly() }
        process.destroyForcibly().waitFor(KILL_WAIT_MILLIS, TimeUnit.MILLISECONDS)
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

    // The child may still be exiting (System.exit in a mutant); give it a moment so the exit code is real.
    private fun failure(
        context: String,
        cause: Throwable,
    ): IllegalStateException {
        val exited = process.waitFor(EXIT_WAIT_MILLIS, TimeUnit.MILLISECONDS)
        val state = if (exited) "exit code ${process.exitValue()}" else "still running, killed"
        if (!exited) destroy()
        stderrDrain.join(EXIT_WAIT_MILLIS)
        close()
        return IllegalStateException(
            "Forked test JVM failed while $context ($state). Last stderr: ${stderr.text().ifBlank { "<empty>" }}",
            cause,
        )
    }

    private sealed interface Event {
        data class Reply(
            val reply: ForkReply,
        ) : Event

        data class Closed(
            val cause: Exception?,
        ) : Event
    }

    private fun handshake() {
        val ready = awaitReply(READY_WAIT_MILLIS) ?: throw IOException("No Ready within $READY_WAIT_MILLIS ms")
        if (ready !is ForkReply.Ready) throw ForkProtocolException("Expected Ready, got $ready")
        ready.requireCompatible()
    }

    companion object {
        /** Starts the JVM and completes the handshake; fails with the exit code and stderr when it cannot. */
        fun start(
            command: List<String>,
            environment: Map<String, String>,
            workingDir: Path?,
        ): ChildProcess {
            val child = spawn(command, environment, workingDir)
            try {
                child.handshake()
            } catch (e: IOException) {
                throw child.failure("starting", e)
            } catch (e: ForkProtocolException) {
                throw child.failure("starting", e)
            }
            return child
        }

        private fun spawn(
            command: List<String>,
            environment: Map<String, String>,
            workingDir: Path?,
        ): ChildProcess {
            val builder = ProcessBuilder(command)
            builder.environment().putAll(environment)
            builder.directory(workingDir?.toFile())
            return try {
                ChildProcess(builder.start())
            } catch (e: IOException) {
                throw IllegalStateException("Cannot start forked test JVM ${command.first()}: ${e.message}", e)
            }
        }
    }
}

// Keeps the last [limit] bytes; the buffer is trimmed at twice that to avoid copying on every append.
private class StderrTail(
    private val limit: Int,
) {
    private val buffer = ByteArrayOutputStream()

    @Synchronized
    fun append(
        bytes: ByteArray,
        length: Int,
    ) {
        buffer.write(bytes, 0, length)
        if (buffer.size() > 2 * limit) {
            val all = buffer.toByteArray()
            buffer.reset()
            buffer.write(all, all.size - limit, limit)
        }
    }

    @Synchronized
    fun text(): String {
        val all = buffer.toByteArray()
        return String(all, maxOf(0, all.size - limit), minOf(all.size, limit), Charsets.UTF_8).trim()
    }
}
