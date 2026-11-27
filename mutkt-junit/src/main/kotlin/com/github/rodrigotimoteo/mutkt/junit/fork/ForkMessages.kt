package com.github.rodrigotimoteo.mutkt.junit.fork

import com.github.rodrigotimoteo.mutkt.application.port.Coverage
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.TestId
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import java.nio.file.Path

internal const val PROTOCOL_VERSION: Int = 7

internal sealed interface ForkRequest {
    /**
     * Run the test classes in [tests], in order (all when null), with [bytes] replacing the mutant's class; the
     * deadline is relative to the start of execution.
     */
    data class Run(
        val mutant: Mutant,
        val bytes: ByteArray,
        val timeoutMillis: Long,
        val tests: List<TestId>? = null,
    ) : ForkRequest {
        init {
            require(timeoutMillis > 0) { "Timeout must be positive: $timeoutMillis" }
        }

        override fun equals(other: Any?): Boolean =
            other is Run &&
                mutant == other.mutant &&
                timeoutMillis == other.timeoutMillis &&
                tests == other.tests &&
                bytes.contentEquals(other.bytes)

        override fun hashCode(): Int =
            31 * (31 * (31 * mutant.hashCode() + bytes.contentHashCode()) + timeoutMillis.hashCode()) +
                tests.hashCode()
    }

    /**
     * Run all tests against the original classes, the timeout as in [Run]; with [probed], against its classes in their
     * place, answering with their coverage.
     */
    data class Baseline(
        val timeoutMillis: Long,
        val probed: ProbedClasses? = null,
    ) : ForkRequest {
        init {
            require(timeoutMillis > 0) { "Timeout must be positive: $timeoutMillis" }
        }
    }

    data object Shutdown : ForkRequest
}

internal sealed interface ForkReply {
    /** Sent once by the child when it is ready for requests. */
    data class Ready(
        val protocolVersion: Int = PROTOCOL_VERSION,
    ) : ForkReply {
        /** Fails fast when the child speaks another protocol version. */
        fun requireCompatible() {
            if (protocolVersion != PROTOCOL_VERSION) {
                throw ForkProtocolException("Child speaks protocol $protocolVersion, expected $PROTOCOL_VERSION")
            }
        }
    }

    /** Sent once per run when the tests begin: classes are loaded and linked and the tests discovered. */
    data object Started : ForkReply

    /**
     * A run's outcome and how long its tests ran, in milliseconds since Started; [retire]: do not reuse the child.
     * A baseline on probed classes carries their [coverage].
     */
    data class Result(
        val outcome: Outcome,
        val elapsedMillis: Long,
        val retire: Boolean = false,
        val coverage: Coverage? = null,
    ) : ForkReply
}

/** Class files with probes under [dir] (a class directory layout; a frame could not hold a large project's). */
internal data class ProbedClasses(
    val dir: Path,
)
