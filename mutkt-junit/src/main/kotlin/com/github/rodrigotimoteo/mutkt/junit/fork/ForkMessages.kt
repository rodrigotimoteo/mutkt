package com.github.rodrigotimoteo.mutkt.junit.fork

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.domain.Mutant

internal const val PROTOCOL_VERSION: Int = 5

internal sealed interface ForkRequest {
    /** Run the tests with [bytes] replacing the mutant's class; the deadline is relative to the start of execution. */
    data class Run(
        val mutant: Mutant,
        val bytes: ByteArray,
        val timeoutMillis: Long,
    ) : ForkRequest {
        init {
            require(timeoutMillis > 0) { "Timeout must be positive: $timeoutMillis" }
        }

        override fun equals(other: Any?): Boolean =
            other is Run &&
                mutant == other.mutant &&
                timeoutMillis == other.timeoutMillis &&
                bytes.contentEquals(other.bytes)

        override fun hashCode(): Int =
            31 * (31 * mutant.hashCode() + bytes.contentHashCode()) + timeoutMillis.hashCode()
    }

    /** Run the tests against the original classes; the timeout is as in [Run]. */
    data class Baseline(
        val timeoutMillis: Long,
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

    /** The outcome of a run and how long its tests ran, in milliseconds since Started. */
    data class Result(
        val outcome: Outcome,
        val elapsedMillis: Long,
    ) : ForkReply
}
