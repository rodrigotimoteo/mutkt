package com.github.rodrigotimoteo.mutation.runner

import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.EngineFilter
import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.slf4j.LoggerFactory

/**
 * Test runner that delegates to the JUnit Platform Launcher.
 *
 * Discovers tests via [DiscoverySelectors.selectClass] for each requested
 * class name, runs them through Jupiter and Vintage engines, and reports
 * per-class outcomes back through [TestResults].
 *
 * Public API kept stable: callers construct
 * `ReflectionTestRunner(classLoader).runTests(classNames)` and consume
 * [TestResults] the same way as the previous reflection-based runner.
 *
 * The no-arg constructor exists so callers that need to run the same
 * runner against multiple classloaders (e.g. the mutation engine) can
 * pass a per-call classloader via [runTests]'s `classLoader` parameter
 * while the underlying [org.junit.platform.launcher.Launcher] is reused
 * across calls. [LauncherFactory.create] is expensive — engine workers
 * process dozens of mutants per thread, and creating a new launcher per
 * mutant dominated the per-mutant overhead.
 */
class ReflectionTestRunner(
    private val classLoader: ClassLoader? = null,
) {
    private val logger = LoggerFactory.getLogger(ReflectionTestRunner::class.java)

    /**
     * Per-thread JUnit Launcher cache. Each worker thread gets one
     * launcher that is reused across all `runTests` calls it makes.
     *
     * Trade-off: the launcher is not formally documented as thread-safe,
     * so we cannot share a single instance across worker threads. A
     * ThreadLocal gives us "one launcher per worker, reused for all
     * mutants that worker processes" — collapsing N launcher creations
     * (one per mutant) to P (one per worker thread, where P << N).
     */
    private val launcherCache = ThreadLocal.withInitial { LauncherFactory.create() }

    data class TestResults(
        val testsFound: Int,
        val testsSucceeded: Int,
        val testsFailed: Int,
        val testsSkipped: Int,
        val failureMessages: List<String>,
        /** Which test classes failed (for per-test kill tracking). */
        val failedTestClasses: Set<String> = emptySet(),
    ) {
        val hasTests: Boolean get() = testsFound > 0
        val hasFailures: Boolean get() = testsFailed > 0
    }

    fun runTests(
        testClassNames: List<String>,
        classLoader: ClassLoader? = null,
    ): TestResults {
        val effectiveLoader = classLoader ?: this.classLoader
        if (effectiveLoader == null) {
            throw IllegalStateException(
                "No ClassLoader available: pass one to runTests or to the constructor",
            )
        }
        if (testClassNames.isEmpty()) {
            return TestResults(0, 0, 0, 0, emptyList(), emptySet())
        }

        val previous = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = effectiveLoader
        try {
            val launcher = launcherCache.get()
            val listener = MutKtListener()
            // Pass the listener per-execute() call. The Launcher interface
            // also has a registerTestExecutionListeners + execute() form,
            // but JUnit 1.10.2 provides no unregister counterpart, so
            // using a fresh listener per execute() is the only way to
            // avoid accumulating N listeners per worker thread.
            for (testClassName in testClassNames) {
                val testClass: Class<*> =
                    try {
                        effectiveLoader.loadClass(testClassName)
                    } catch (e: Exception) {
                        val cause =
                            if (e is java.lang.reflect.InvocationTargetException) {
                                e.targetException?.message
                            } else {
                                e.message
                            }
                        val errorMsg = "Could not load test class $testClassName: $cause"
                        logger.warn(errorMsg)
                        listener.recordLoadFailure(testClassName, errorMsg)
                        continue
                    }
                val request: LauncherDiscoveryRequest =
                    LauncherDiscoveryRequestBuilder.request()
                        .selectors(DiscoverySelectors.selectClass(testClass))
                        .filters(EngineFilter.includeEngines("junit-jupiter", "junit-vintage"))
                        .build()
                val testPlan = launcher.discover(request)
                launcher.execute(testPlan, listener)
            }

            return listener.toResults()
        } finally {
            Thread.currentThread().contextClassLoader = previous
        }
    }

    /**
     * Aggregates test execution events. Each [executionStarted] increments
     * the "found" count; [executionFinished] with status SUCCESSFUL/FAILED
     * updates the success/failure tallies. Container identifiers (class
     * level) are ignored when tallying.
     */
    private class MutKtListener : TestExecutionListener {
        var found = 0
        var succeeded = 0
        var failed = 0
        var skipped = 0
        val failureMessages = mutableListOf<String>()
        val failedTestClasses = mutableSetOf<String>()
        val loadedTestClasses = mutableSetOf<String>()

        fun recordLoadFailure(
            className: String,
            message: String,
        ) {
            failed++
            failureMessages.add(message)
            failedTestClasses.add(className)
        }

        override fun executionStarted(testIdentifier: TestIdentifier) {
            if (testIdentifier.isTest) {
                found++
                testIdentifier.className()?.let { loadedTestClasses.add(it) }
            }
        }

        override fun executionSkipped(
            testIdentifier: TestIdentifier,
            reason: String?,
        ) {
            if (testIdentifier.isTest) {
                if (isDisabledReason(reason)) {
                    // @Disabled tests are reported as SKIPPED; the original
                    // reflection runner kept them out of testsFound so the
                    // count stays accurate for mutation analysis.
                    skipped++
                    return
                }
                // Any other skip (parameter resolution, lifecycle) is
                // treated as a real failure for the runner consumer.
                found++
                skipped++
                failed++
                testIdentifier.className()?.let {
                    failedTestClasses.add(it)
                    loadedTestClasses.add(it)
                }
                if (reason != null) {
                    failureMessages.add("${testIdentifier.displayName}: $reason")
                }
            }
        }

        override fun executionFinished(
            testIdentifier: TestIdentifier,
            result: TestExecutionResult,
        ) {
            when (result.status) {
                TestExecutionResult.Status.SUCCESSFUL -> if (testIdentifier.isTest) succeeded++
                TestExecutionResult.Status.ABORTED -> if (testIdentifier.isTest) skipped++
                TestExecutionResult.Status.FAILED -> {
                    val throwable = result.throwable.orElse(null)
                    val detail = describe(throwable)
                    if (testIdentifier.isTest) {
                        failed++
                        testIdentifier.className()?.let { failedTestClasses.add(it) }
                        failureMessages.add("${testIdentifier.displayName}: $detail")
                    } else {
                        // A container failure (class-level @BeforeAll/AfterAll
                        // or a single parameterized test that failed argument
                        // resolution) maps to a single test result.
                        found++
                        failed++
                        testIdentifier.className()?.let {
                            failedTestClasses.add(it)
                            loadedTestClasses.add(it)
                        }
                        failureMessages.add("${testIdentifier.displayName}: $detail")
                    }
                }
            }
        }

        private fun isDisabledReason(reason: String?): Boolean {
            if (reason == null) return false
            // Jupiter formats disabled reasons as
            // "public void com.example.Foo.disabledTest() is @Disabled"
            // or with the @Disabled message suffix.
            return reason.contains("@Disabled") || reason.endsWith(" is @Disabled")
        }

        private fun describe(throwable: Throwable?): String {
            if (throwable == null) return "unknown failure"
            return throwable.message ?: throwable.javaClass.simpleName
        }

        fun toResults(): TestResults =
            TestResults(
                testsFound = found,
                testsSucceeded = succeeded,
                testsFailed = failed,
                testsSkipped = skipped,
                failureMessages = failureMessages.toList(),
                failedTestClasses = failedTestClasses.toSet(),
            )

        private fun TestIdentifier.className(): String? {
            if (!isTest) {
                val source = source.orElse(null) ?: return null
                return when (source) {
                    is ClassSource -> source.className
                    is MethodSource -> source.className
                    else -> null
                }
            }
            val source = source.orElse(null) ?: return null
            return when (source) {
                is ClassSource -> source.className
                is MethodSource -> source.className
                else -> null
            }
        }
    }
}
