package com.github.rodrigotimoteo.mutkt.asm

/** A site found by [discover]; [ordinal] is its index among the operator's sites of that method. */
internal data class Found(
    val className: String,
    val methodName: String,
    val descriptor: String,
    val ordinal: Int,
    val line: Int?,
    val description: String,
)
