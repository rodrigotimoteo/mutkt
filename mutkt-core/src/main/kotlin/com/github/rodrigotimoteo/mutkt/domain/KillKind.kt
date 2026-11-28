package com.github.rodrigotimoteo.mutkt.domain

/** How the test that killed a mutant failed: an [AssertionError] (any subclass) or any other throwable. */
public enum class KillKind {
    ASSERTION,
    EXCEPTION,
}
