package com.github.rodrigotimoteo.mutkt.application.port

import com.github.rodrigotimoteo.mutkt.domain.Mutant
import kotlin.time.Duration

/** Runs the project's tests against a mutant; throws [TestExecutionStopped] when it cannot continue. */
public interface TestExecutor {
    /**
     * Runs the test classes in [tests], in that order (all when null), with [bytes] replacing the mutant's class.
     * [deadline] is a timeout measured from the start of test execution, not class loading.
     */
    public fun run(
        mutant: Mutant,
        bytes: ByteArray,
        deadline: Duration,
        tests: List<TestId>? = null,
    ): Outcome

    /**
     * Runs all tests against the original classes, in the same environment as [run]; [deadline] as there.
     * Anything but [Outcome.Survived] or [Outcome.NoTests] means the suite is not green.
     * With [instrumented] its classes replace the originals and [Baseline.coverage] tells which probes each test class
     * reached; it is null when this executor cannot read them.
     */
    public fun baseline(
        deadline: Duration,
        instrumented: Instrumented? = null,
    ): Baseline
}
