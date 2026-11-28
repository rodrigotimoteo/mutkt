package com.github.rodrigotimoteo.mutkt.application.port

import com.github.rodrigotimoteo.mutkt.domain.KillKind

/** Result of running the tests against one mutant; mapping it to a `MutantStatus` is the use case's job. */
public sealed interface Outcome {
    /** A test failed; [by] names the first failure observed: class and method, plus the invocation when known. */
    public data class Killed(
        val by: String,
        /** How that failure happened; a child process that died is an exception. */
        val kind: KillKind,
        /** The first failure's `toString()` on one line, capped; null when a child process died. */
        val cause: String? = null,
        /** The test class that failed; null when none is known, as when a child process died. */
        val test: TestId? = null,
    ) : Outcome

    public data object Survived : Outcome

    /** No test exercised the mutant. */
    public data object NoTests : Outcome

    /** The mutant could not be loaded or run. */
    public data class LoadError(
        val message: String,
    ) : Outcome

    public data object Timeout : Outcome
}
