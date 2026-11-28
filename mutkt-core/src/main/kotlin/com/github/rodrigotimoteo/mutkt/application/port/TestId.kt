package com.github.rodrigotimoteo.mutkt.application.port

/**
 * A test class by binary name, or one test [method] in it (named by its engine's unique id): the units in which tests
 * are selected and run.
 */
public data class TestId(
    val className: String,
    val method: String? = null,
) {
    /** True for a whole test class, false for one of its methods. */
    public val isClass: Boolean get() = method == null
}
