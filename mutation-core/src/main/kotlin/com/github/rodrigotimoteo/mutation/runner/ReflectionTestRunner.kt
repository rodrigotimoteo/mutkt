package com.github.rodrigotimoteo.mutation.runner

import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.EngineFilter
import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.junit.platform.launcher.TagFilter
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
    /**
     * JUnit Platform engine IDs to include when discovering tests. Default
     * covers both Jupiter (JUnit 5) and Vintage (JUnit 4) so a project
     * mixing the two works out of the box. Pass a custom list to restrict
     * discovery to a specific engine, e.g. `listOf("junit-jupiter")` to
     * skip Vintage on a pure-Jupiter project.
     */
    private val engineIds: List<String> = listOf("junit-jupiter", "junit-vintage"),
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

    /**
     * Release the per-thread JUnit Launcher for the calling thread. The
     * launcher holds engine-level resources that are otherwise retained
     * for the lifetime of the worker thread, so callers that finish a
     * mutation run must invoke this to avoid leaks. The launcher is
     * recreated lazily on the next [runTests] call from the same thread.
     */
    fun cleanup() {
        launcherCache.remove()
    }

    data class TestResults(
        val testsFound: Int,
        val testsSucceeded: Int,
        val testsFailed: Int,
        val testsSkipped: Int,
        val failureMessages: List<String>,
        /** Which test classes failed (for per-test kill tracking). */
        val failedTestClasses: Set<String> = emptySet(),
        /** Number of test classes that failed to LOAD (infrastructure errors, not test failures). */
        val loadFailures: Int = 0,
    ) {
        val hasTests: Boolean get() = testsFound > 0
        val hasFailures: Boolean get() = testsFailed > 0

        /** True when failures are all from class load errors (no real test assertion failures). */
        val hasOnlyLoadFailures: Boolean get() = loadFailures > 0 && testsFailed == loadFailures
    }

    fun runTests(
        testClassNames: List<String>,
        classLoader: ClassLoader? = null,
        includeTags: Set<String> = emptySet(),
        excludeTags: Set<String> = emptySet(),
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

            // Phase 1: load every requested test class. Load failures are
            // recorded on the listener so the result aggregation still
            // reports them as a `loadFailures` count and surfaces the
            // error message — matching the previous per-class loop's
            // behavior.
            val loadedClasses = mutableListOf<Class<*>>()
            for (testClassName in testClassNames) {
                try {
                    loadedClasses.add(effectiveLoader.loadClass(testClassName))
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
                }
            }

            // Phase 2: a single LauncherDiscoveryRequest covering all
            // loaded classes, a single discover(), a single execute().
            // The previous per-class loop paid the cost of N discovery
            // passes (engine introspection, classpath scanning) for N
            // test classes; with many test classes that overhead
            // dominated the runner's wall time. Engine and tag filters
            // are applied once on the same request builder and
            // propagate to every selector uniformly.
            if (loadedClasses.isNotEmpty()) {
                val selectors =
                    loadedClasses
                        .map { DiscoverySelectors.selectClass(it) }
                        .toTypedArray()
                val request: LauncherDiscoveryRequest =
                    LauncherDiscoveryRequestBuilder.request()
                        .selectors(*selectors)
                        .filters(EngineFilter.includeEngines(*engineIds.toTypedArray()))
                        .apply {
                            // Honor @Tag / @Tags / @EnabledIf / @EnabledOnOs style
                            // filtering that the user expresses through
                            // `MutationEngineConfig.includeTags` /
                            // `excludeTags`. JUnit Platform applies
                            // includeTags as a positive match and excludeTags
                            // as a negative match; both compose with the
                            // engine filter above. TagFilter on its own
                            // filters by JUnit 5 @Tag; @EnabledIf / @EnabledOnOs
                            // resolve at execution time and remain honored.
                            if (includeTags.isNotEmpty()) {
                                filters(TagFilter.includeTags(*includeTags.toTypedArray()))
                            }
                            if (excludeTags.isNotEmpty()) {
                                filters(TagFilter.excludeTags(*excludeTags.toTypedArray()))
                            }
                        }
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
        var loadFailures = 0
        val failureMessages = mutableListOf<String>()
        val failedTestClasses = mutableSetOf<String>()
        val loadedTestClasses = mutableSetOf<String>()

        fun recordLoadFailure(
            className: String,
            message: String,
        ) {
            // Load failures are tracked separately so the engine can distinguish
            // infrastructure errors (load failures) from real test assertion
            // failures. Keep `failed` incremented for backward compatibility with
            // callers that only inspect `testsFailed` / `hasFailures`.
            failed++
            loadFailures++
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
            // NOTE: this matches by reason-string substring (e.g. "is
            // @Disabled", "is @Ignore") rather than by reflecting on
            // `TestIdentifier.source` for the @Disabled / @Ignore
            // annotation. Substring matching is sufficient for current
            // JUnit 5 / JUnit 4 releases and avoids the cost of a
            // per-skipped-test classloader lookup. If a future JUnit
            // release changes the skip-reason format, the substring
            // constants below are the only thing to update.
            if (reason.contains("@Disabled") || reason.endsWith(" is @Disabled")) return true
            if (reason.contains("@Ignored") || reason.endsWith(" is @Ignored")) return true
            if (reason.contains("@Ignore") || reason.endsWith(" is @Ignore")) return true
            return false
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
                loadFailures = loadFailures,
            )

        private fun TestIdentifier.className(): String? {
            // `source` is an Optional; `isTest` only affects whether the
            // identifier is a test method or a container. The mapping to a
            // class name is the same in both cases, so we collapse the
            // previously-duplicated `when` branches.
            val source = source.orElse(null) ?: return null
            return when (source) {
                is ClassSource -> source.className
                is MethodSource -> source.className
                else -> null
            }
        }
    }
}
