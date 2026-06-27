package com.github.rodrigotimoteo.mutation.engine

import com.github.rodrigotimoteo.mutation.DEFAULT_TIMEOUT_MS
import com.github.rodrigotimoteo.mutation.LOG_PREFIX
import com.github.rodrigotimoteo.mutation.analysis.KillSetStorage
import com.github.rodrigotimoteo.mutation.analysis.SubsumptionAnalyzer
import com.github.rodrigotimoteo.mutation.analysis.TestStrengthOrdering
import com.github.rodrigotimoteo.mutation.analysis.WeakMutationAnalyzer
import com.github.rodrigotimoteo.mutation.baseline.BaselineStorage
import com.github.rodrigotimoteo.mutation.cache.MutKtCache
import com.github.rodrigotimoteo.mutation.classloader.MutantClassLoaderFactory
import com.github.rodrigotimoteo.mutation.coverage.CoverageAnalyzer
import com.github.rodrigotimoteo.mutation.model.Mutation
import com.github.rodrigotimoteo.mutation.model.MutationReport
import com.github.rodrigotimoteo.mutation.model.MutationResult
import com.github.rodrigotimoteo.mutation.model.MutationStatus
import com.github.rodrigotimoteo.mutation.mutator.InlinedFinallyDetector
import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import com.github.rodrigotimoteo.mutation.mutator.Mutator
import com.github.rodrigotimoteo.mutation.runner.ReflectionTestRunner
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.PatternSyntaxException

/**
 * Maximum number of seconds to wait for in-flight mutants to finish during
 * the orderly executor shutdown before escalating to `shutdownNow()`. The
 * previous literal `10` was duplicated below for the `shutdownNow` path.
 */
private const val EXECUTOR_SHUTDOWN_TIMEOUT_SEC = 10L

/**
 * Maximum number of seconds to wait for in-flight mutants to finish after
 * `shutdownNow()` has been called. The previous literal `5` was duplicated
 * inline in `runMutants`.
 */
private const val EXECUTOR_SHUTDOWN_NOW_TIMEOUT_SEC = 5L

/**
 * Minimum number of results required before post-hoc subsumption analysis
 * runs. The subsumption comparison is O(n^2) within each (class, method)
 * group, so for very small result sets the analysis is more overhead than
 * benefit — and a 0–9 result set is almost always a degenerate case (e.g.
 * a single-class run with a couple of operators disabled) where the
 * "subsumed" output would be noise.
 */
private const val MIN_SUBSUMPTION_MUTATIONS = 10

/**
 * Main mutation testing engine.
 *
 * Orchestrates mutation generation, test execution, and result aggregation.
 * The engine scans classes for mutation points, generates mutants, runs tests
 * against each mutant, and reports which mutations were killed or survived.
 *
 * Features:
 * - Parallel mutant testing with configurable workers
 * - Coverage-guided mutation filtering (JaCoCo)
 * - Weak mutation analysis (skip unreachable mutations)
 * - Subsumption analysis (skip redundant mutations)
 * - Test strength ordering (run strongest tests first)
 * - Incremental analysis (only test changed classes)
 * - Regex filtering (include/exclude patterns)
 * - File-based caching (skip previously killed mutants)
 * - Baseline comparison (track changes across runs)
 *
 * Example:
 * ```kotlin
 * val engine = MutationEngine(
 *     enabledOperators = MutationOperator.MVP_OPERATORS,
 *     timeoutMs = 30000,
 *     maxParallelMutants = 4,
 *     includePatterns = listOf("com.example.service.*"),
 *     excludePatterns = listOf("com.example.test.*"),
 * )
 * val report = engine.runMutationTesting(classFiles, testClassNames)
 * println("Score: ${report.killedPercentage}%")
 * ```
 *
 * Concurrency: the engine is **single-shot, single-thread**. Create one
 * engine per run; do not invoke [runMutationTesting] concurrently from
 * multiple threads on the same instance — the per-run scratch state
 * (`classFilesMap`, `allTestClassBytes`, `sourceFileCache`) would race
 * and the second caller would observe the first caller's bytes. The
 * shared [ReflectionTestRunner] has the same constraint.
 *
 * @property enabledOperators Mutation operators to apply
 * @property timeoutMs Timeout per mutant in milliseconds
 * @property maxParallelMutants Number of parallel mutant executions.
 *           `0` falls back to `Runtime.getRuntime().availableProcessors()`.
 * @property enableInlinedFinally Filter mutations in inlined finally blocks
 * @property enableTestOrdering Order tests by historical strength
 * @property changedClasses Only test these classes (incremental mode)
 * @property includePatterns Regex patterns for classes to include
 * @property excludePatterns Regex patterns for classes to exclude
 * @property enableCache Use file-based caching for results
 * @property enableSubsumption Skip redundant mutations
 * @property enableWeakMutation Skip unreachable mutations
 * @property projectDir Project root directory for caching/baseline
 * @property excludedMethods Method names to skip during scanning (substring match)
 * @property maxMutationsPerClass Cap on mutations per class (`0` = no cap)
 * @property targetTestPatterns Regex patterns for test classes to include
 * @property excludeTestPatterns Regex patterns for test classes to exclude
 * @property includeTags JUnit @Tag names to include (also honored for @EnabledIf / @EnabledOnOs at execution time)
 * @property excludeTags JUnit @Tag names to exclude
 * @property engineIds JUnit Platform engine IDs to include when discovering tests.
 *           Default `["junit-jupiter", "junit-vintage", "junit-platform-suite-engine"]`
 *           — restrict to e.g. `["junit-jupiter"]` to skip Vintage on a pure-Jupiter project.
 * @see MutationReport for result details
 */
@Suppress("LongParameterList", "TooManyFunctions")
class MutationEngine @JvmOverloads constructor(
    private val enabledOperators: Set<MutationOperator> = MutationOperator.MVP_OPERATORS,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    maxParallelMutants: Int = 4,
    private val enableInlinedFinally: Boolean = true,
    private val enableTestOrdering: Boolean = true,
    private val changedClasses: Set<String> = emptySet(),
    private val includePatterns: List<String> = emptyList(),
    private val excludePatterns: List<String> = emptyList(),
    private val enableCache: Boolean = false,
    private val enableSubsumption: Boolean = true,
    private val enableWeakMutation: Boolean = true,
    private val projectDir: File? = null,
    private val excludedMethods: Set<String> = emptySet(),
    private val maxMutationsPerClass: Int = 0,
    private val targetTestPatterns: List<String> = emptyList(),
    private val excludeTestPatterns: List<String> = emptyList(),
    private val includeTags: Set<String> = emptySet(),
    private val excludeTags: Set<String> = emptySet(),
    private val engineIds: List<String> =
        listOf(
            "junit-jupiter",
            "junit-vintage",
            "junit-platform-suite-engine",
        ),
) {
    /**
     * Config-based constructor. Preferred for new code: groups the 19
     * engine parameters into a single [MutationEngineConfig] so call
     * sites read as named groups (Filter, Scan, Execution, ...) rather
     * than a long parameter list.
     *
     * @sample samples.engine.runWithConfig
     */
    constructor(config: MutationEngineConfig) : this(
        enabledOperators = config.enabledOperators,
        timeoutMs = config.timeoutMs,
        maxParallelMutants = config.maxParallelMutants,
        enableInlinedFinally = config.enableInlinedFinally,
        enableTestOrdering = config.enableTestOrdering,
        changedClasses = config.changedClasses,
        includePatterns = config.includePatterns,
        excludePatterns = config.excludePatterns,
        enableCache = config.enableCache,
        enableSubsumption = config.enableSubsumption,
        enableWeakMutation = config.enableWeakMutation,
        projectDir = config.projectDir,
        excludedMethods = config.excludedMethods,
        maxMutationsPerClass = config.maxMutationsPerClass,
        targetTestPatterns = config.targetTestPatterns,
        excludeTestPatterns = config.excludeTestPatterns,
        includeTags = config.includeTags,
        excludeTags = config.excludeTags,
        engineIds = config.engineIds,
    )
    private val logger = LoggerFactory.getLogger(MutationEngine::class.java)
    private val mutator = Mutator(enabledOperators, excludedMethods)
    private val coverageAnalyzer = CoverageAnalyzer()
    private val weakMutationAnalyzer = WeakMutationAnalyzer()
    private val subsumptionAnalyzer = SubsumptionAnalyzer()
    private val killSetStorage = projectDir?.let { KillSetStorage(it) }
    private val testStrengthOrdering = projectDir?.let { TestStrengthOrdering(it) }
    private val cache = projectDir?.let { MutKtCache(it) }
    private val baselineStorage = projectDir?.let { BaselineStorage(it) }

    /** 0 = use all available processors. */
    private val parallelism = if (maxParallelMutants > 0) maxParallelMutants else Runtime.getRuntime().availableProcessors()

    // Compiled regex patterns for include/exclude filtering. Patterns are
    // validated at construction time so an invalid regex fails fast with a
    // descriptive error instead of throwing from a deep call site during
    // mutation filtering.
    private val compiledIncludePatterns = compilePatterns(includePatterns, "includePatterns")
    private val compiledExcludePatterns = compilePatterns(excludePatterns, "excludePatterns")

    // Compiled regex patterns for test class filtering. Same fail-fast
    // rationale as the include/exclude patterns above: a bad regex here
    // would otherwise only surface deep inside `filterTestClassNames` at
    // execution time, where the user has no way to identify which
    // pattern string was at fault.
    private val compiledTargetTestPatterns = compilePatterns(targetTestPatterns, "targetTestPatterns")
    private val compiledExcludeTestPatterns = compilePatterns(excludeTestPatterns, "excludeTestPatterns")

    // Per-run scratch state. These two maps are mutable `var` fields set
    // by `runMutationTesting` and read by helpers like `getOriginalClassBytes`
    // and `runMutants`. They are intentionally instance state because the
    // engine is treated as a single-shot, single-run object: create an
    // engine, run it, throw it away.
    //
    // Concurrency warning: a single `MutationEngine` instance is NOT safe
    // for concurrent `runMutationTesting` calls — the assignments below race
    // and the second caller observes the first caller's bytes. For parallel
    // runs, give each thread its own engine instance. (The shared
    // `ReflectionTestRunner` has the same constraint; see its docs.)

    // Store class files for cache lookups
    private var classFilesMap: Map<String, ByteArray> = emptyMap()

    // Test class bytecode indexed by slashed class name. Populated by
    // `runMutationTesting` so `runMutants` can hand the bytes to the
    // mutation classloader when defining test classes (the classpath
    // does NOT include the compiled test .class files).
    private var allTestClassBytes: Map<String, ByteArray> = emptyMap()

    /**
     * Single [ReflectionTestRunner] shared across all mutants in a run.
     * The runner caches the JUnit Launcher per worker thread (see
     * [ReflectionTestRunner]), so reusing the runner across mutants
     * turns N launcher creations into P (one per worker thread).
     * Trade-off: the runner holds a per-thread launcher; if two
     * concurrent runs shared a single engine instance they would
     * collide on those launchers — keep it scoped to one run.
     */
    private val sharedTestRunner = ReflectionTestRunner(engineIds = engineIds)

    /**
     * Runs mutation testing on the given classpath.
     *
     * @param classFiles Map of slashed class name to compiled bytecode
     *        (e.g. `"com/example/Foo"` → bytes). Must not be empty.
     * @param testClassNames Fully qualified test class names to run
     *        against each mutant. May be empty (all mutations will
     *        report `NO_COVERAGE`).
     * @param testClassBytes Bytecode for the test classes — required
     *        when the test classes are not on the classpath provided
     *        by [testClassLoader].
     * @param coverageExecFile Optional JaCoCo `.exec` file. When present
     *        and valid, drives coverage-guided filtering of mutations
     *        (drops mutations in uncovered lines) and weak-mutation
     *        analysis (drops mutations in unrun classes).
     * @param testClassLoader Optional parent classloader for the
     *        mutation test classloader. Defaults to the engine's own
     *        classloader when null.
     * @return A [MutationReport] containing per-mutation results and
     *         aggregate statistics.
     * @throws IllegalArgumentException if any of [MutationEngineConfig.includePatterns],
     *         [MutationEngineConfig.excludePatterns],
     *         [MutationEngineConfig.targetTestPatterns], or
     *         [MutationEngineConfig.excludeTestPatterns] is not a valid
     *         regex (raised at engine construction, not here).
     * @throws java.util.concurrent.TimeoutException never — the
     *         per-mutant timeout is enforced internally and reported
     *         as [com.github.rodrigotimoteo.mutation.model.MutationStatus.TIMEOUT].
     * @sample samples.engine.minimalRun
     * @see MutationEngineConfig for grouped configuration
     */
    @JvmOverloads
    fun runMutationTesting(
        classFiles: Map<String, ByteArray>,
        testClassNames: List<String>,
        testClassBytes: Map<String, ByteArray> = emptyMap(),
        coverageExecFile: File? = null,
        testClassLoader: ClassLoader? = null,
    ): MutationReport {
        // Store class files for cache lookups
        classFilesMap = classFiles
        // Stash the test class bytes so `runMutants` can map test class
        // names to their bytecode (the prior implementation filtered
        // `classFiles` (main classes) and left test bytes unreachable,
        // making every test `ClassNotFoundException` and collapsing all
        // mutations to NO_COVERAGE).
        allTestClassBytes = testClassBytes

        // A reused engine can outlive the source files its previous run
        // looked at; `findSourceCode` would otherwise hand back stale
        // snippets for paths that no longer exist on disk. Clear before
        // each run so the first mutation in a class forces a fresh stat.
        sourceFileCache.clear()

        val startTime = System.currentTimeMillis()
        val allClassFiles = classFiles + testClassBytes

        // Parse coverage once and reuse for both filter and weak-mutation
        // analysis. Without this, the .exec file is re-parsed for every
        // mutation in filterByCoverage and again for getCoveredLines.
        val coverageData =
            if (coverageExecFile != null && coverageExecFile.exists()) {
                coverageAnalyzer.loadExecutionData(coverageExecFile)
            } else {
                CoverageAnalyzer.CoverageData.Empty
            }

        val mutationsToTest = prepareMutations(classFiles, coverageData)
        val (filteredTestNames, orderedTestNames) = prepareTestNames(testClassNames)
        val (mutationsForRun, cachedResults) = filterByCache(mutationsToTest)

        val (results, killSets) =
            executeMutants(
                mutationsForRun,
                allClassFiles,
                orderedTestNames,
                testClassLoader,
            )
        logger.info(
            "Tested ${results.size} mutations (${results.size * 1000 / maxOf(System.currentTimeMillis() - startTime, 1)} mutations/sec)",
        )

        val allResults = results + cachedResults

        // Post-test bookkeeping: cache, test-strength history, kill-set
        // storage, subsumption analysis, baseline.
        recordPostTestResults(results, filteredTestNames, killSets)
        val finalResults = applySubsumption(allResults, killSets)
        saveBaselineIfNeeded(finalResults)

        val totalTime = System.currentTimeMillis() - startTime
        return buildReport(finalResults, totalTime)
    }

    /**
     * Phase 1: scan + filter mutations. Reads coverage data once (via
     * the caller) and threads the parsed [CoverageAnalyzer.CoverageData]
     * through the coverage, weak-mutation, and inlined-finally filters.
     */
    private fun prepareMutations(
        classFiles: Map<String, ByteArray>,
        coverageData: CoverageAnalyzer.CoverageData,
    ): List<Pair<MutationInfo, ByteArray>> {
        val scanStart = System.currentTimeMillis()
        val allMutations = generateAllMutations(classFiles)
        val scanTime = System.currentTimeMillis() - scanStart
        logger.info("Scanned ${allMutations.size} mutations in ${scanTime}ms")

        if (allMutations.isEmpty()) {
            logger.warn("No mutations found. Check enabled operators and class filter patterns.")
        }

        val afterRegex = filterByPatterns(allMutations)
        val skippedRegex = allMutations.size - afterRegex.size
        if (skippedRegex > 0) {
            logger.info(
                "Regex: skipped $skippedRegex mutations (include=${includePatterns.size}, exclude=${excludePatterns.size})",
            )
        }

        val afterLimit =
            if (maxMutationsPerClass > 0) {
                limitMutationsPerClass(afterRegex)
            } else {
                afterRegex
            }

        val afterCoverage =
            if (coverageData is CoverageAnalyzer.CoverageData.Valid) {
                filterByCoverage(afterLimit, classFiles, coverageData)
            } else {
                afterLimit
            }
        logger.info("Testing ${afterCoverage.size} mutations after coverage filtering")

        val afterWeak =
            if (enableWeakMutation && coverageData is CoverageAnalyzer.CoverageData.Valid) {
                applyWeakMutation(afterCoverage, coverageData)
            } else {
                afterCoverage
            }

        val afterInlined =
            if (enableInlinedFinally) {
                filterInlinedFinally(afterWeak, classFiles)
            } else {
                afterWeak
            }

        return if (changedClasses.isNotEmpty()) {
            filterIncremental(afterInlined)
        } else {
            afterInlined
        }
    }

    /**
     * Apply weak-mutation filtering using the already-parsed coverage data.
     * Mutations in classes whose lines were not exercised by any test are
     * dropped, since no test could possibly kill them.
     */
    private fun applyWeakMutation(
        mutations: List<Pair<MutationInfo, ByteArray>>,
        coverageData: CoverageAnalyzer.CoverageData.Valid,
    ): List<Pair<MutationInfo, ByteArray>> {
        val coveredLinesMap = getCoveredLines(coverageData)
        val reachableMutations =
            weakMutationAnalyzer.filterUnreachable(
                mutations.map { it.first },
                coveredLinesMap,
            )
        val reachableSet = reachableMutations.toSet()
        val (reachable, unreachable) =
            mutations.partition { (mutation, _) -> mutation in reachableSet }
        if (unreachable.isNotEmpty()) {
            logger.info("Weak mutation: skipped ${unreachable.size} unreachable mutations")
        }
        return reachable
    }

    /**
     * Drop mutations that fall inside a Kotlin inlined `finally` block.
     * These mutations don't represent distinct observable behavior (the
     * same bytecode is reachable from the outer call site) and would
     * inflate the mutation count without adding coverage signal.
     */
    private fun filterInlinedFinally(
        mutations: List<Pair<MutationInfo, ByteArray>>,
        classFiles: Map<String, ByteArray>,
    ): List<Pair<MutationInfo, ByteArray>> {
        val detector = InlinedFinallyDetector()
        val blockCache = mutableMapOf<String, List<InlinedFinallyDetector.InlinedFinallyBlock>>()
        val filtered =
            mutations.filter { (mutation, _) ->
                val classBytes = classFiles[mutation.className.replace('.', '/')]
                if (classBytes != null) {
                    val blocks = blockCache.getOrPut(mutation.className) { detector.detect(classBytes) }
                    !detector.isInInlinedBlock(mutation.lineNumber, blocks)
                } else {
                    true
                }
            }
        val skipped = mutations.size - filtered.size
        if (skipped > 0) {
            logger.info("Skipped $skipped mutations in inlined finally blocks")
        }
        return filtered
    }

    /**
     * Keep only mutations whose class is in the changed-classes set
     * (incremental analysis). Mutations in unchanged classes are skipped
     * because their tests have already been exercised in a previous run.
     */
    private fun filterIncremental(mutations: List<Pair<MutationInfo, ByteArray>>): List<Pair<MutationInfo, ByteArray>> {
        val filtered =
            mutations.filter { (mutation, _) -> mutation.className in changedClasses }
        val skipped = mutations.size - filtered.size
        if (skipped > 0) {
            logger.info(
                "Incremental: skipped $skipped " +
                    "mutations (only testing ${changedClasses.size} changed classes)",
            )
        }
        return filtered
    }

    /**
     * Phase 2: apply test-class include/exclude regex, then order the
     * remaining test classes by historical strength (if enabled). The
     * returned pair is `(unfilteredNames, orderedNames)` — callers need
     * the unfiltered set to credit per-test strength scores correctly.
     */
    private fun prepareTestNames(testClassNames: List<String>): Pair<List<String>, List<String>> {
        val filtered = filterTestClassNames(testClassNames)
        val ordered =
            if (enableTestOrdering && testStrengthOrdering != null) {
                testStrengthOrdering.orderTests(filtered)
            } else {
                filtered
            }
        return filtered to ordered
    }

    /**
     * Phase 3: execute the per-mutant test runs. Wraps the parallel
     * execution in a try/finally so the per-thread JUnit Launchers held
     * by the shared runner are released even on cancellation.
     */
    private fun executeMutants(
        mutations: List<Pair<MutationInfo, ByteArray>>,
        allClassFiles: Map<String, ByteArray>,
        orderedTestNames: List<String>,
        testClassLoader: ClassLoader?,
    ): Pair<List<MutationResult>, Map<String, Set<String>>> =
        try {
            runMutants(mutations, allClassFiles, orderedTestNames, testClassLoader)
        } finally {
            // Release the per-thread JUnit Launchers held by the shared
            // runner. Without this, every worker thread keeps its
            // launcher alive for the JVM lifetime — a leak that grows
            // with parallelism and prevents engine instances from being
            // garbage-collected.
            sharedTestRunner.cleanup()
        }

    /**
     * Phase 4: post-test bookkeeping. Updates the on-disk cache, the
     * test-strength history, and the kill-set storage. Split from
     * [runMutationTesting] so the main flow stays at one-screen height.
     */
    private fun recordPostTestResults(
        results: List<MutationResult>,
        filteredTestNames: List<String>,
        killSets: Map<String, Set<String>>,
    ) {
        if (enableCache && cache != null) {
            updateCache(results)
        }
        if (enableTestOrdering && testStrengthOrdering != null) {
            updateTestStrength(results, filteredTestNames, killSets)
            testStrengthOrdering.flushHistory()
        }
        if (enableSubsumption && killSetStorage != null) {
            if (changedClasses.isNotEmpty()) {
                killSetStorage.saveMerged(killSets)
            } else {
                killSetStorage.save(killSets)
            }
        }
    }

    /**
     * Phase 5: post-hoc subsumption. Any mutation whose kill set is a
     * strict subset of another mutation's kill set within the same
     * (class, method) is marked [MutationStatus.SUBSUMED] rather than
     * killed or survived — it adds no information beyond its
     * subsuming sibling.
     *
     * Subsumption only considers actually-killed mutations: ERROR
     * mutants can carry non-empty `failedTests` (load failures still
     * surface as failed test classes) but those are infrastructure
     * errors, not real kills. Mixing them in would let a broken
     * mutant be reported as SUBSUMED, hiding the underlying problem.
     */
    private fun applySubsumption(
        allResults: List<MutationResult>,
        killSets: Map<String, Set<String>>,
    ): List<MutationResult> {
        if (!enableSubsumption || allResults.size <= MIN_SUBSUMPTION_MUTATIONS) {
            return allResults
        }
        val killedResults =
            allResults.filter {
                it.status == MutationStatus.KILLED || it.status == MutationStatus.WEAK_KILLED
            }
        if (killedResults.size < 2) return allResults

        val killedIds = killedResults.map { it.mutation.id }.toSet()
        val killedKillSets = killSets.filterKeys { it in killedIds }
        val killedMutations = killedResults.map { it.mutation }

        val (_, rawSubsumed) = subsumptionAnalyzer.analyze(killedMutations, killedKillSets)
        // The analyzer may return IDs that no longer pass the killed
        // filter (defensive — analyzer should already honor it, but we
        // re-intersect here so a future analyzer change cannot smuggle
        // a SUBSUMED status onto a non-killed result).
        val subsumed = rawSubsumed.intersect(killedIds)
        if (subsumed.isEmpty()) return allResults
        logger.info("Subsumption: ${subsumed.size} redundant mutations identified")
        return allResults.map { result ->
            if (result.mutation.id in subsumed) {
                result.copy(status = MutationStatus.SUBSUMED)
            } else {
                result
            }
        }
    }

    /**
     * Phase 6: persist the current run as a baseline for the next run.
     * Incremental runs merge with the existing baseline so unchanged
     * classes keep their prior results.
     */
    private fun saveBaselineIfNeeded(finalResults: List<MutationResult>) {
        if (baselineStorage != null) {
            saveBaseline(finalResults, useMerge = changedClasses.isNotEmpty())
        }
    }

    private fun generateAllMutations(classFiles: Map<String, ByteArray>): List<Pair<MutationInfo, ByteArray>> {
        // H-R3-perf-2: scan + mutate in parallel. Each class is independent
        // (its own ASM parse, its own mutated bytes), so a parallel stream
        // is safe. The stream uses ForkJoinPool.commonPool(), whose
        // parallelism defaults to available processors — usually a good
        // match for the engine's `parallelism` setting.
        return classFiles.entries
            .parallelStream()
            .flatMap { (_, classBytes) -> mutator.generateMutants(classBytes).stream() }
            .toList()
    }

    /**
     * Filter mutations by include/exclude regex patterns.
     */
    private fun filterByPatterns(mutations: List<Pair<MutationInfo, ByteArray>>): List<Pair<MutationInfo, ByteArray>> {
        if (compiledIncludePatterns.isEmpty() && compiledExcludePatterns.isEmpty()) {
            return mutations
        }

        return mutations.filter { (mutation, _) ->
            val className = mutation.className

            // Check exclude patterns first
            val excluded = compiledExcludePatterns.any { it.containsMatchIn(className) }
            if (excluded) return@filter false

            // If include patterns specified, must match at least one
            if (compiledIncludePatterns.isNotEmpty()) {
                compiledIncludePatterns.any { it.containsMatchIn(className) }
            } else {
                true
            }
        }
    }

    /**
     * Get covered lines from an already-parsed [CoverageAnalyzer.CoverageData.Valid].
     * Kept as a private helper so the engine has a single place that
     * logs the "weak mutation" summary.
     */
    private fun getCoveredLines(coverageData: CoverageAnalyzer.CoverageData.Valid): Map<String, Set<Int>> {
        val coveredLinesMap = coverageAnalyzer.getCoveredLines(coverageData, classFilesMap)
        val totalLines = coveredLinesMap.values.flatten().size
        logger.info("Weak mutation: $totalLines covered lines across ${coveredLinesMap.size} classes")
        return coveredLinesMap
    }

    /**
     * Filter mutations by cache (skip previously tested).
     */
    private fun filterByCache(
        mutations: List<Pair<MutationInfo, ByteArray>>,
    ): Pair<List<Pair<MutationInfo, ByteArray>>, List<MutationResult>> {
        if (!enableCache || cache == null) {
            return mutations to emptyList()
        }

        val toTest = mutableListOf<Pair<MutationInfo, ByteArray>>()
        val cached = mutableListOf<MutationResult>()

        for ((mutation, mutatedBytes) in mutations) {
            val classBytes = getOriginalClassBytes(mutation.className)
            if (classBytes != null) {
                val classHash = cache.computeClassHash(classBytes)
                val occurrenceIndex = mutation.metadata["occurrenceIndex"]?.toIntOrNull() ?: 0
                val cachedStatus =
                    cache.lookup(
                        classHash,
                        mutation.operator.operatorName,
                        mutation.methodName,
                        mutation.lineNumber,
                        occurrenceIndex,
                    )
                if (cachedStatus != null) {
                    cached.add(
                        MutationResult(
                            mutation = toMutation(mutation, mutatedBytes),
                            status = cachedStatus,
                            executionTimeMs = 0,
                        ),
                    )
                    continue
                }
            }
            toTest.add(mutation to mutatedBytes)
        }

        return toTest to cached
    }

    /**
     * Update cache with new results.
     */
    private fun updateCache(results: List<MutationResult>) {
        if (cache == null) return

        for (result in results) {
            val classBytes = getOriginalClassBytes(result.mutation.className)
            if (classBytes != null) {
                val classHash = cache.computeClassHash(classBytes)
                cache.store(
                    classHash,
                    result.mutation.operator.operatorName,
                    result.mutation.methodName,
                    result.mutation.lineNumber,
                    result.mutation.getOccurrenceIndex(),
                    result.status,
                )
            }
        }
    }

    /**
     * Update test strength ordering with results.
     * Records per-mutant outcome: only test classes that actually killed
     * the mutation get credit, not all test classes.
     *
     * Old loop called `recordResults` once per (mutation, test) pair,
     * hitting a file lock + JSON write per call. Now we accumulate
     * per-test (kills, runs) deltas in a Map and call `recordResults`
     * once per test class.
     */
    private fun updateTestStrength(
        results: List<MutationResult>,
        testClassNames: List<String>,
        killSets: Map<String, Set<String>>,
    ) {
        if (testStrengthOrdering == null) return

        val totals = HashMap<String, IntArray>(testClassNames.size * 2)
        for (testClass in testClassNames) {
            totals[testClass] = intArrayOf(0, 0)
        }
        for (result in results) {
            val killingTests = killSets[result.mutation.id] ?: emptySet()
            for (testClass in testClassNames) {
                val entry = totals.getValue(testClass)
                if (testClass in killingTests) entry[0] = entry[0] + 1
                entry[1] = entry[1] + 1
            }
        }
        for ((testClass, entry) in totals) {
            testStrengthOrdering.recordResults(testClass, entry[0], entry[1])
        }
    }

    /**
     * Save baseline for future comparison.
     *
     * The baseline key includes [com.github.rodrigotimoteo.mutation.mutator.MutationInfo]'s
     * `occurrenceIndex` so multiple mutations on the same line with the
     * same operator are tracked as separate rows. The previous
     * `(operator, lineNumber, status)` triple collapsed them, so a
     * class with two same-line same-operator mutations would persist
     * the survivor status and silently lose the other.
     */
    private fun saveBaseline(
        results: List<MutationResult>,
        useMerge: Boolean = false,
    ) {
        if (baselineStorage == null) return

        val baselineData =
            results.groupBy { it.mutation.className }
                .mapValues { (_, classResults) ->
                    classResults.map { result ->
                        com.github.rodrigotimoteo.mutation.baseline.BaselineEntry(
                            operator = result.mutation.operator.operatorName,
                            lineNumber = result.mutation.lineNumber,
                            occurrenceIndex = result.mutation.getOccurrenceIndex(),
                            status = result.status,
                        )
                    }
                }

        if (useMerge) {
            baselineStorage.saveMerged(baselineData)
        } else {
            baselineStorage.save(baselineData)
        }
    }

    /**
     * Get original class bytes from classFiles map.
     */
    private fun getOriginalClassBytes(className: String): ByteArray? {
        val classKey = className.replace('.', '/')
        return classFilesMap[classKey]
    }

    private fun filterByCoverage(
        allMutations: List<Pair<MutationInfo, ByteArray>>,
        classFiles: Map<String, ByteArray>,
        coverageData: CoverageAnalyzer.CoverageData.Valid,
    ): List<Pair<MutationInfo, ByteArray>> {
        // Per-class covered-lines cache. JaCoCo's analyzer parses the
        // .exec file plus walks the class structure per call, so we MUST
        // call `getCoveredLinesForClass` once per unique class — calling
        // it per mutation was the O(N²) hot spot in C-R3-4a (a class with
        // 200 mutations triggered 200 JaCoCo analyses of the same bytes).
        val coveredLinesByClass = mutableMapOf<String, Set<Int>>()

        val filtered = mutableListOf<Pair<MutationInfo, ByteArray>>()
        for ((mutation, mutatedBytes) in allMutations) {
            val classKey = mutation.className.replace('.', '/')
            val classBytes = classFiles[classKey]
            if (classBytes == null) {
                // Class bytes missing from the classpath map. We can't
                // run the JaCoCo analyzer for this class, so we err on
                // the side of testing the mutation rather than
                // silently dropping it — a key-mismatch here would
                // otherwise shrink the test set for no good reason.
                filtered.add(mutation to mutatedBytes)
                continue
            }
            val coveredLines =
                coveredLinesByClass.getOrPut(mutation.className) {
                    coverageAnalyzer.getCoveredLinesForClass(
                        coverageData,
                        classKey,
                        classBytes,
                    )
                }
            if (mutation.lineNumber in coveredLines) {
                filtered.add(mutation to mutatedBytes)
            }
        }
        return filtered
    }

    private fun runMutants(
        mutations: List<Pair<MutationInfo, ByteArray>>,
        classFiles: Map<String, ByteArray>,
        testClassNames: List<String>,
        testClassLoader: ClassLoader? = null,
    ): Pair<List<MutationResult>, Map<String, Set<String>>> {
        if (mutations.isEmpty()) return emptyList<MutationResult>() to emptyMap()

        val parentLoader = testClassLoader ?: this.javaClass.classLoader
        // Filter the test class bytes (not the main classFiles) so the
        // mutation classloader defines test classes from the testClassesDir
        // bytes. Previously this filtered `classFiles` (main classes only),
        // leaving testClassByteMap empty and forcing the parent URLClassLoader
        // to try to resolve compiled test .class files that are NOT on the
        // classpath — resulting in ClassNotFoundException and `testsFound=0`
        // (i.e. all mutations reported as NO_COVERAGE).
        val testClassByteMap =
            allTestClassBytes.filterKeys { key ->
                testClassNames.any { it.replace('.', '/') == key }
            }

        // Group mutations by target class. Each group shares a BaseProjectClassLoader
        // that loads all non-target project classes once, avoiding repeated defineClass.
        val mutationsByClass = mutations.groupBy { it.first.className }
        logger.info("${mutations.size} mutations across ${mutationsByClass.size} classes")

        // Pre-create 3-tier classloader group per target class (shared across mutations).
        // H-R3-perf-1: the middle TestClassLoader defines test classes once per group,
        // avoiding O(M × T) re-defineClass cost when mutations per target class is large.
        val baseLoaders =
            mutableMapOf<String, com.github.rodrigotimoteo.mutation.classloader.MutantClassLoaderGroup>()
        for ((className, _) in mutationsByClass) {
            val targetKey = className.replace('.', '/')
            baseLoaders[className] =
                MutantClassLoaderFactory.createGroup(
                    parentLoader, classFiles, testClassByteMap, targetKey,
                )
        }

        val results = mutableListOf<MutationResult>()
        val killSets = mutableMapOf<String, Set<String>>()
        val completedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val startTime = System.currentTimeMillis()
        val executor: ExecutorService =
            Executors.newFixedThreadPool(parallelism) { runnable ->
                Thread(runnable, "mutation-worker").apply { isDaemon = true }
            }
        logger.info("Running $parallelism parallel workers")

        try {
            val futures =
                mutations.mapIndexed { index, (mutation, mutatedBytes) ->
                    val baseLoader =
                        requireNotNull(baseLoaders[mutation.className]) {
                            "No BaseProjectClassLoader for ${mutation.className}"
                        }
                    index to
                        executor.submit(
                            Callable {
                                runSingleMutantBatched(mutation, mutatedBytes, classFiles, baseLoader, testClassNames)
                            },
                        )
                }

            for ((index, future) in futures) {
                try {
                    val (result, failedTests) = future.get(timeoutMs, TimeUnit.MILLISECONDS)
                    results.add(result)
                    // Only credit kill sets for mutations that were
                    // actually killed. ERROR mutants (load failures)
                    // and NO_COVERAGE mutants can still report non-empty
                    // `failedTests`, but those are infrastructure noise
                    // and would poison test-strength history and
                    // subsumption analysis if stored as if they were
                    // real kills.
                    if (failedTests.isNotEmpty() &&
                        (result.status == MutationStatus.KILLED || result.status == MutationStatus.WEAK_KILLED)
                    ) {
                        killSets[result.mutation.id] = failedTests
                    }
                    // Print progress. Guard `rate` against a zero
                    // denominator: when the first mutation finishes
                    // within the same millisecond the worker started,
                    // `elapsed` is 0 and `count / elapsed` produces
                    // `Infinity` (a Double), which would print as
                    // "Inf mut/s" in the progress bar.
                    val count = completedCount.incrementAndGet()
                    val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                    val rate = if (elapsed > 0.0) count / elapsed else 0.0
                    val pct = count * 100 / mutations.size
                    System.err.print(
                        "\r$LOG_PREFIX Progress: $count/${mutations.size} ($pct%) ${elapsed}s ${rate.toInt()} mut/s",
                    )
                } catch (e: java.util.concurrent.TimeoutException) {
                    future.cancel(true)
                    logger.warn("Mutation timed out after ${timeoutMs}ms: ${mutations[index].first}")
                    results.add(
                        MutationResult(
                            mutation = toMutation(mutations[index].first, mutatedBytes = mutations[index].second),
                            status = MutationStatus.TIMEOUT,
                            executionTimeMs = timeoutMs,
                            errorMessage = "Timeout after ${timeoutMs}ms",
                        ),
                    )
                } catch (e: CancellationException) {
                    // The future was cancelled (typically by the
                    // InterruptedException branch below or by a
                    // previous TimeoutException on the same future).
                    // Without this branch, `CancellationException`
                    // would fall into the catch-all `Exception` and be
                    // reported as a mutant ERROR, polluting the report
                    // with phantom failures for cancellations the user
                    // explicitly requested. Skip and move on.
                    continue
                } catch (e: java.util.concurrent.ExecutionException) {
                    logger.error("Mutation execution failed: ${mutations[index].first}", e.cause)
                    results.add(
                        MutationResult(
                            mutation = toMutation(mutations[index].first, mutatedBytes = mutations[index].second),
                            status = MutationStatus.ERROR,
                            errorMessage = "${e.cause?.javaClass?.simpleName}: ${e.cause?.message}",
                        ),
                    )
                } catch (e: InterruptedException) {
                    // Re-raise the interrupt flag so callers further up
                    // (e.g. a Gradle cancellation) can observe it, then
                    // exit the result-collection loop. The outer
                    // `finally` (executor.shutdown, base-loader close,
                    // sharedTestRunner.cleanup) still runs, so the run
                    // is left in a clean state before the exception
                    // propagates out of `runMutants`.
                    logger.warn("Mutation testing interrupted at mutant index $index")
                    // Cancel any in-flight mutations so they do not keep
                    // running on worker threads after the caller has
                    // bailed out. Previously only the current future
                    // was implicitly abandoned; sibling futures kept
                    // running for the full `timeoutMs` each, leaking
                    // CPU and (worse) potentially mutating shared
                    // classloader state after `runMutants` returned.
                    futures.forEach { it.second.cancel(true) }
                    executor.shutdownNow()
                    Thread.currentThread().interrupt()
                    break
                } catch (e: Exception) {
                    // Catch-all for unexpected failures (OOM, reflection
                    // errors, etc.). Without the explicit log, the
                    // mutation is silently converted to ERROR and the
                    // stack trace is lost. Log first, then convert.
                    logger.error("Mutant execution failed: ${mutations[index].first}", e)
                    results.add(
                        MutationResult(
                            mutation = toMutation(mutations[index].first, mutatedBytes = mutations[index].second),
                            status = MutationStatus.ERROR,
                            errorMessage = "${e.javaClass.simpleName}: ${e.message}",
                        ),
                    )
                }
            }
        } finally {
            executor.shutdown()
            if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                executor.shutdownNow()
                if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_NOW_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                    logger.warn("Executor did not terminate after shutdownNow — threads may leak")
                }
            }
            // Terminate the progress bar line so subsequent log output does
            // not overwrite the final progress percentage.
            System.err.println()
            // Close the shared BaseProjectClassLoader instances. Per-mutant
            // MutationClassLoader instances are closed in runSingleMutantBatched.
            // Each close is wrapped so a failing close does not prevent the
            // remaining loaders from being released.
            for ((targetClass, group) in baseLoaders) {
                try {
                    invokeClose(group.base)
                } catch (e: Exception) {
                    logger.debug("Error closing BaseProjectClassLoader for $targetClass: ${e.message}")
                }
            }
            baseLoaders.clear()
        }

        return results to killSets
    }

    /**
     * Batched mutation testing: uses shared 3-tier classloader group per target class.
     * Uses pre-computed mutated bytes from generateAllMutations (no re-application).
     */
    private fun runSingleMutantBatched(
        mutation: MutationInfo,
        mutatedBytes: ByteArray,
        classFiles: Map<String, ByteArray>,
        baseLoader: com.github.rodrigotimoteo.mutation.classloader.MutantClassLoaderGroup,
        testClassNames: List<String>,
    ): Pair<MutationResult, Set<String>> {
        val startTime = System.currentTimeMillis()

        val targetClassKey = mutation.className.replace('.', '/')

        val classLoader =
            MutantClassLoaderFactory.createMutationLoader(
                baseLoader,
                targetClassKey,
                mutatedBytes,
                classFiles,
            )

        try {
            val (status, failedTestClasses) = runTestsWithClassLoader(classLoader, testClassNames)

            return MutationResult(
                mutation = toMutation(mutation, mutatedBytes),
                status = status,
                executionTimeMs = System.currentTimeMillis() - startTime,
            ) to failedTestClasses
        } finally {
            // Close the per-mutant classloader so any resources it holds
            // (caches, jar handles) are released before the next mutant
            // run. BaseProjectClassLoader is shared across mutations of the
            // same target and is closed in runMutants' finally block.
            try {
                invokeClose(classLoader)
            } catch (e: Exception) {
                logger.debug("Error closing per-mutant classloader for $targetClassKey: ${e.message}")
            }
        }
    }

    private fun runTestsWithClassLoader(
        classLoader: ClassLoader,
        testClassNames: List<String>,
    ): Pair<MutationStatus, Set<String>> {
        // Reuse the engine-level ReflectionTestRunner so the per-thread
        // JUnit Launcher is cached and reused across mutants. Each mutant
        // passes its own per-mutant classloader as a run-time argument.
        val results = sharedTestRunner.runTests(testClassNames, classLoader, includeTags, excludeTags)

        if (!results.hasTests) {
            return if (results.failureMessages.isNotEmpty()) {
                MutationStatus.ERROR to emptySet()
            } else {
                MutationStatus.NO_COVERAGE to emptySet()
            }
        }

        val status =
            when {
                // Load failures (no real test assertion failures) are infrastructure
                // errors, not mutant kills. Distinguish before falling through to KILLED.
                results.hasOnlyLoadFailures -> MutationStatus.ERROR
                results.hasFailures -> MutationStatus.KILLED
                results.failureMessages.isNotEmpty() -> MutationStatus.ERROR
                else -> MutationStatus.SURVIVED
            }

        return status to results.failedTestClasses
    }

    private fun toMutation(
        info: MutationInfo,
        mutatedBytes: ByteArray = ByteArray(0),
    ): Mutation {
        val (sourceFile, sourceCode) = findSourceCode(info.className, info.lineNumber)
        val originalBytes = getOriginalClassBytes(info.className) ?: ByteArray(0)
        val occurrenceIndex = info.metadata["occurrenceIndex"] ?: "0"
        val id =
            "${info.operator.operatorName}::${info.className}::${info.methodName}::${info.lineNumber}::$occurrenceIndex"
        return Mutation(
            id = id,
            className = info.className,
            methodName = info.methodName,
            methodDescriptor = info.methodDescriptor,
            operator = info.operator,
            lineNumber = info.lineNumber,
            sourceFile = sourceFile,
            sourceCode = sourceCode,
            originalBytecode = originalBytes,
            mutatedBytecode = mutatedBytes,
            description = info.description,
        )
    }

    /**
     * Cache of source-file contents keyed by absolute path. A single
     * class can produce dozens of mutations; previously `findSourceCode`
     * re-read the same `.kt`/`.java` from disk for every mutation in
     * the class. Caching the read eliminates N×file I/O per class.
     *
     * [ConcurrentHashMap] is required because the worker threads spawned
     * by `runMutants` call `findSourceCode` (and therefore touch this
     * cache) in parallel with the main thread. A plain [mutableMapOf]
     * is not safe for concurrent reads/writes and would risk losing
     * cached entries or, under the JDK, throwing on structural
     * modifications during iteration.
     *
     * An [emptyList] entry acts as the "path does not exist" sentinel:
     * [ConcurrentHashMap] rejects null values from `put` / `putIfAbsent`,
     * so the previous nullable-map design had to be adapted. The lookup
     * path treats an empty list the same as a missing file (skip) and
     * an empty list from a real but empty source file is functionally
     * identical for our purposes — there is no line snippet to return.
     */
    private val sourceFileCache: ConcurrentHashMap<String, List<String>> = ConcurrentHashMap()

    /**
     * Pre-computed source roots for [findSourceCode]. Building the list
     * inside the per-mutation call path was cheap per call but repeated
     * thousands of times per run; the list itself never changes between
     * mutations, so it is built once at construction and reused. The
     * `kotlin`/`java` suffix variants are collapsed by the `suffixes`
     * join below.
     */
    private val sourceRoots: List<String> by lazy {
        val bases =
            listOf(
                "src/main",
                "src/commonMain",
                "src/jvmMain",
                "src/androidMain",
                "src/iosMain",
                "src/iosArm64Main",
                "src/iosX64Main",
                "src/iosSimulatorArm64Main",
                "src/linuxMain",
                "src/macosMain",
                "src/mingwX64Main",
                "src/mingwX86Main",
                "src/jsMain",
                "src/wasmJsMain",
                "src/wasmWasiMain",
            )
        val suffixes = listOf("kotlin", "java")
        bases.flatMap { base -> suffixes.map { "$base/$it" } }
    }

    private fun findSourceCode(
        className: String,
        lineNumber: Int,
    ): Pair<String?, String?> {
        if (lineNumber <= 0) return null to null
        // `File(File?, String)` NPEs at the JVM level when the parent is
        // null. Bail out before any I/O so the report simply shows no
        // source snippet instead of crashing the engine when no
        // projectDir was wired in (e.g. consumer-side usage that does
        // not need source-level reporting).
        if (projectDir == null) return null to null
        val relativePath = className.replace('.', '/') + ".kt"
        val javaPath = className.replace('.', '/') + ".java"
        // Covers pure-JVM (main/test) plus all common KMP source set
        // directories. The list is pre-computed once in [sourceRoots];
        // previously it was rebuilt for every mutation in the class.
        for (srcDir in sourceRoots) {
            for (path in listOf(relativePath, javaPath)) {
                val sourceFile = File(projectDir, "$srcDir/$path")
                val cacheKey = sourceFile.path
                if (sourceFile.exists()) {
                    // `emptyList()` cache value means "file path
                    // resolves to nothing readable" — read once and
                    // store. A class can produce dozens of mutations;
                    // the previous code re-read the same .kt/.java
                    // from disk for every one, wasting N×file I/O.
                    val lines =
                        sourceFileCache.getOrPut(cacheKey) {
                            if (sourceFile.exists()) sourceFile.readLines() else emptyList()
                        }
                    if (lines.isEmpty()) continue
                    if (lineNumber <= lines.size) {
                        val snippet =
                            lines.subList(
                                maxOf(0, lineNumber - 2),
                                minOf(lines.size, lineNumber + 1),
                            ).joinToString("\n")
                        return sourceFile.path to snippet
                    }
                    return sourceFile.path to null
                }
                // Negative cache for non-existent paths so we don't stat
                // the same missing file on every mutation in the class.
                // [ConcurrentHashMap] rejects null values, so use
                // [emptyList] as the "not found" sentinel.
                sourceFileCache.putIfAbsent(cacheKey, emptyList())
            }
        }
        return null to null
    }

    private fun buildReport(
        results: List<MutationResult>,
        totalTime: Long,
    ): MutationReport {
        val killed = results.count { it.status == MutationStatus.KILLED }
        val weakKilled = results.count { it.status == MutationStatus.WEAK_KILLED }
        val survived = results.count { it.isSurvived }
        val errors = results.count { it.status == MutationStatus.ERROR }
        val timeouts = results.count { it.status == MutationStatus.TIMEOUT }
        val noCoverage = results.count { it.status == MutationStatus.NO_COVERAGE }
        val subsumed = results.count { it.status == MutationStatus.SUBSUMED }
        // Invariant: all buckets must sum to totalMutations. Subsumed and
        // weak-killed were previously counted in `total` but lived in no
        // bucket, so reports could show killed+survived < total.
        val accountedFor = killed + weakKilled + survived + errors + timeouts + noCoverage + subsumed
        if (accountedFor != results.size) {
            logger.warn(
                "MutationReport bucket mismatch: buckets=$accountedFor total=${results.size} " +
                    "(unexpected status in results list)",
            )
        }

        return MutationReport(
            results = results,
            totalMutations = results.size,
            killedMutations = killed,
            survivedMutations = survived,
            errorMutations = errors,
            timeoutMutations = timeouts,
            noCoverageMutations = noCoverage,
            totalExecutionTimeMs = totalTime,
            subsumedMutations = subsumed,
            weakKilledMutations = weakKilled,
        )
    }

    /**
     * Limit mutations per class to maxMutationsPerClass.
     * Truncates to the first N mutations per class (deterministic).
     */
    private fun limitMutationsPerClass(mutations: List<Pair<MutationInfo, ByteArray>>): List<Pair<MutationInfo, ByteArray>> {
        val grouped = mutations.groupBy { it.first.className }
        val limited =
            grouped.flatMap { (className, classMutations) ->
                if (classMutations.size > maxMutationsPerClass) {
                    logger.info("Limiting $className: ${classMutations.size} → $maxMutationsPerClass mutations")
                    classMutations.take(maxMutationsPerClass)
                } else {
                    classMutations
                }
            }
        val totalBefore = mutations.size
        val totalAfter = limited.size
        if (totalBefore != totalAfter) {
            logger.info("Max mutations per class: limited $totalBefore → $totalAfter mutations")
        }
        return limited
    }

    /**
     * Filter test class names by targetTestPatterns and excludeTestPatterns.
     *
     * Patterns are pre-compiled in the constructor (see
     * [compiledTargetTestPatterns] / [compiledExcludeTestPatterns]) so
     * invalid regex fails fast at engine construction. This method only
     * applies the already-compiled patterns to the incoming test class
     * names, avoiding the per-run `Regex(...)` allocation that would
     * otherwise be paid for every call.
     */
    private fun filterTestClassNames(testClassNames: List<String>): List<String> {
        if (compiledTargetTestPatterns.isEmpty() && compiledExcludeTestPatterns.isEmpty()) {
            return testClassNames
        }

        return testClassNames.filter { name ->
            val matchesInclude =
                compiledTargetTestPatterns.isEmpty() ||
                    compiledTargetTestPatterns.any { it.containsMatchIn(name) }
            val matchesExclude = compiledExcludeTestPatterns.any { it.containsMatchIn(name) }
            matchesInclude && !matchesExclude
        }
    }

    /**
     * Compile a list of regex pattern strings, wrapping any
     * [PatternSyntaxException] in a descriptive [IllegalArgumentException]
     * that names the offending pattern and the source parameter. This makes
     * user-facing configuration errors obvious at engine construction time
     * instead of bubbling up from a deep call site during mutation
     * filtering.
     */
    private fun compilePatterns(
        patterns: List<String>,
        sourceName: String,
    ): List<Regex> =
        patterns.map { pattern ->
            try {
                Regex(pattern)
            } catch (e: PatternSyntaxException) {
                throw IllegalArgumentException(
                    "Invalid regex in $sourceName: \"$pattern\" " +
                        "(${e.description ?: "syntax error"} near index ${e.index})",
                    e,
                )
            }
        }

    /**
     * Invoke [ClassLoader.close] via reflection. `ClassLoader.close` is a
     * Java 7+ method but the Kotlin compiler in some module graphs resolves
     * `kotlin.jvm.internal.ClassLoader` (which lacks `close`) instead of
     * `java.lang.ClassLoader`. Reflection sidesteps the resolution issue
     * while still releasing the underlying resources. Falls through as a
     * no-op for older JVMs that do not expose `close`.
     */
    private fun invokeClose(classLoader: ClassLoader) {
        try {
            val closeMethod = ClassLoader::class.java.getMethod("close")
            closeMethod.invoke(classLoader)
        } catch (_: NoSuchMethodException) {
            // JVM predates Java 7 — no close support, nothing to do.
        } catch (_: java.lang.reflect.InvocationTargetException) {
            // The loader's close threw — surface as debug noise.
            logger.debug("ClassLoader.close() threw for $classLoader")
        } catch (_: IllegalAccessException) {
            // Should not happen for ClassLoader.close, but stay defensive.
        }
    }
}
