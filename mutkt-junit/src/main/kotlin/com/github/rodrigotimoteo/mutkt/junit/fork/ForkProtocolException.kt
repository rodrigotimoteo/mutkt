package com.github.rodrigotimoteo.mutkt.junit.fork

/** The peer sent, or the stream ended inside, a malformed frame. */
internal class ForkProtocolException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
