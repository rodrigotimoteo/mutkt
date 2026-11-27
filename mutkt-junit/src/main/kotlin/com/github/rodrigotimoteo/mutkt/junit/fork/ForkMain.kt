@file:JvmName("ForkMain")

package com.github.rodrigotimoteo.mutkt.junit.fork

import com.github.rodrigotimoteo.mutkt.application.port.TimedOutcome
import com.github.rodrigotimoteo.mutkt.junit.JUnitTestExecutor
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Path
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds

private const val EXIT_PROTOCOL = 2
private const val EXIT_UNEXPECTED = 3

/**
 * The child's exit code when an [OutOfMemoryError] ends it, which the parent reports as the kill's cause. A mutant
 * calling `System.exit(4)` is reported the same way; `-XX:+ExitOnOutOfMemoryError` exits with 3 before this
 * catch runs (reported as `JVM exit 3`) and `-XX:+CrashOnOutOfMemoryError` aborts by signal (an ERROR).
 */
internal const val EXIT_OUT_OF_MEMORY: Int = 4

/**
 * Child JVM entry point; `args[0]` is the startup file. Exit 0: shutdown or EOF, 2: protocol, 3: failure,
 * 4: out of memory.
 */
@Suppress("TooGenericExceptionCaught")
internal fun main(args: Array<String>) {
    // Frames own the real stdout; System.out goes to stderr. FileDescriptor.out writes bypass this (known limit).
    val protocol = DataOutputStream(BufferedOutputStream(System.out))
    System.setOut(System.err)
    val status =
        try {
            serve(Path.of(args.single()), DataInputStream(BufferedInputStream(System.`in`)), protocol)
            0
        } catch (e: ForkProtocolException) {
            report(e)
            EXIT_PROTOCOL
        } catch (e: OutOfMemoryError) {
            report(e)
            EXIT_OUT_OF_MEMORY
        } catch (e: Throwable) {
            report(e)
            EXIT_UNEXPECTED
        }
    // Abandoned mutant threads are daemons, but exiting explicitly keeps the guarantee independent of that.
    exitProcess(status)
}

private fun serve(
    startupFile: Path,
    input: DataInputStream,
    output: DataOutputStream,
) {
    val startup = ForkStartup.parse(startupFile)
    JUnitTestExecutor(
        readClassFiles(startup.projectClassDir),
        readClassFiles(startup.testClassDir),
        // The class directories answer package scans (getResources("a/b/")); their classes still come from memory.
        listOf(startup.projectClassDir, startup.testClassDir) + startup.classpath,
        startup.engines,
        startup.includeTags,
        startup.excludeTags,
        startup.codeSources,
    ).use { runForkLoop(input, output, it) }
}

// Stderr is the child's diagnostic channel; the parent captures it.
@Suppress("PrintStackTrace")
private fun report(e: Throwable) = e.printStackTrace()

/**
 * Announces readiness, then answers each run request until shutdown or a clean end of input; a result asks the parent
 * to retire this child when [retire] says so after the run.
 */
internal fun runForkLoop(
    input: DataInputStream,
    output: DataOutputStream,
    executor: JUnitTestExecutor,
    retire: () -> Boolean = ::needsRetirement,
) {
    writeReply(output, ForkReply.Ready())
    while (true) {
        when (val request = readRequest(input)) {
            is ForkRequest.Run -> {
                val deadline = request.timeoutMillis.milliseconds
                val started = { writeReply(output, ForkReply.Started) }
                writeResult(output, executor.run(request.mutant, request.bytes, deadline, started), retire())
            }
            is ForkRequest.Baseline -> {
                val started = { writeReply(output, ForkReply.Started) }
                writeResult(output, executor.baseline(request.timeoutMillis.milliseconds, started), retire())
            }
            ForkRequest.Shutdown, null -> return
        }
    }
}

private fun writeResult(
    output: DataOutputStream,
    result: TimedOutcome,
    retire: Boolean,
) = writeReply(output, ForkReply.Result(result.outcome, result.elapsed.inWholeMilliseconds, retire))
