package com.github.rodrigotimoteo.mutkt.junit.fork

/** A child that did not report Started within the preparation limit; [output] is the tail of its stderr. */
internal class PreparationStalled(
    failure: IllegalStateException,
    val output: String,
) : IllegalStateException(failure.message, failure.cause)
