package com.github.rodrigotimoteo.mutation.engine

/**
 * Verification mode for controlling how surviving mutations are handled.
 */
enum class VerificationMode {
    /**
     * Default: surviving mutations fail the build.
     */
    STRICT,

    /**
     * Surviving mutations are reported but don't fail the build.
     */
    LENIENT,

    /**
     * Skip mutation testing entirely.
     */
    DISABLED,
}
