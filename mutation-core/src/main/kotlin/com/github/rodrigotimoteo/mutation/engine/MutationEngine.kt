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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.PatternSyntaxException

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
 * @property enabledOperators Mutation operators to apply
 * @property timeoutMs Timeout per mutant in milliseconds
 * @property maxParallelMutants Number of parallel mutant executions
 * @property enableInlinedFinally Filter mutations in inlined finally blocks
 * @property enableTestOrdering Order tests by historical strength
 * @property changedClasses Only test these classes (incremental mode)
 * @property includePatterns Regex patterns for classes to include
 * @property excludePatterns Regex patterns for classes to exclude
 * @property enableCache Use file-based caching for results
 * @property enableSubsumption Skip redundant mutations
 * @property enableWeakMutation Skip unreachable mutations
 * @property projectDir Project root directory for caching/baseline
 * @see MutationReport for result details
 */
class MutationEngine(
    private val enabledOperators: Set<MutationOperator> = MutationOperator.MVP_OPERATORS,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    maxParallelMutants: Int = 0,
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
) {
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
    private val sharedTestRunner = ReflectionTestRunner()

    /**
     * Runs mutation testing on the given classpath.
     */
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

        val startTime = System.currentTimeMillis()
        val scanStart = System.currentTimeMillis()
        val allMutations = generateAllMutations(classFiles)
        val scanTime = System.currentTimeMillis() - scanStart
        logger.info("Scanned ${allMutations.size} mutations in ${scanTime}ms")

        if (allMutations.isEmpty()) {
            logger.warn("No mutations found. Check enabled operators and class filter patterns.")
        }

        // Combine class files with test class files
        val allClassFiles = classFiles + testClassBytes

        // Filter by regex patterns (include/exclude)
        val mutationsAfterRegex = filterByPatterns(allMutations)
        val skippedRegex = allMutations.size - mutationsAfterRegex.size
        if (skippedRegex > 0) {
            logger.info(
                "Regex: skipped $skippedRegex mutations (include=${includePatterns.size}, exclude=${excludePatterns.size})",
            )
        }

        // Limit mutations per class if configured
        val mutationsAfterLimit =
            if (maxMutationsPerClass > 0) {
                limitMutationsPerClass(mutationsAfterRegex)
            } else {
                mutationsAfterRegex
            }

        // Filter mutations by coverage if available
        val mutationsAfterCoverage =
            if (coverageExecFile != null && coverageExecFile.exists()) {
                filterByCoverage(mutationsAfterLimit, classFiles, coverageExecFile)
            } else {
                mutationsAfterLimit
            }
        logger.info("Testing ${mutationsAfterCoverage.size} mutations after coverage filtering")

        // Weak mutation analysis (skip unreachable mutations)
        val mutationsAfterWeak =
            if (enableWeakMutation && coverageExecFile != null && coverageExecFile.exists()) {
                val coveredLinesMap = getCoveredLines(coverageExecFile)
                val reachableMutations =
                    weakMutationAnalyzer.filterUnreachable(
                        mutationsAfterCoverage.map { it.first },
                        coveredLinesMap,
                    )
                val reachableSet = reachableMutations.toSet()
                val (reachable, unreachable) =
                    mutationsAfterCoverage.partition { (mutation, _) ->
                        mutation in reachableSet
                    }
                if (unreachable.isNotEmpty()) {
                    logger.info("Weak mutation: skipped ${unreachable.size} unreachable mutations")
                }
                reachable
            } else {
                mutationsAfterCoverage
            }

        // Filter out mutations in inlined finally blocks (duplicates)
        val mutationsAfterInlined =
            if (enableInlinedFinally) {
                val detector = InlinedFinallyDetector()
                val blockCache = mutableMapOf<String, List<InlinedFinallyDetector.InlinedFinallyBlock>>()
                mutationsAfterWeak.filter { (mutation, _) ->
                    val classBytes = classFiles[mutation.className.replace('.', '/')]
                    if (classBytes != null) {
                        val blocks = blockCache.getOrPut(mutation.className) { detector.detect(classBytes) }
                        !detector.isInInlinedBlock(mutation.lineNumber, blocks)
                    } else {
                        true
                    }
                }
            } else {
                mutationsAfterWeak
            }
        val skippedInlined = mutationsAfterWeak.size - mutationsAfterInlined.size
        if (skippedInlined > 0) {
            logger.info("Skipped $skippedInlined mutations in inlined finally blocks")
        }

        // Order test classes by effectiveness (if history available)
        val filteredTestNames = filterTestClassNames(testClassNames)
        val orderedTestNames =
            if (enableTestOrdering && testStrengthOrdering != null) {
                testStrengthOrdering.orderTests(filteredTestNames)
            } else {
                filteredTestNames
            }

        // Filter by changed classes for incremental analysis
        val mutationsAfterIncremental =
            if (changedClasses.isNotEmpty()) {
                val filtered =
                    mutationsAfterInlined.filter { (mutation, _) ->
                        mutation.className in changedClasses
                    }
                val skippedIncremental = mutationsAfterInlined.size - filtered.size
                if (skippedIncremental > 0) {
                    logger.info(
                        "Incremental: skipped $skippedIncremental " +
                            "mutations (only testing ${changedClasses.size} changed classes)",
                    )
                }
                filtered
            } else {
                mutationsAfterInlined
            }

        // Check cache for previously tested mutations
        val (mutationsToTest, cachedResults) = filterByCache(mutationsAfterIncremental)
        if (cachedResults.isNotEmpty()) {
            logger.info("Cache: ${cachedResults.size} mutations already tested")
        }

        // Run tests against each mutant
        val testStart = System.currentTimeMillis()
        val (results, killSets) = runMutants(mutationsToTest, allClassFiles, orderedTestNames, testClassLoader)
        val testTime = System.currentTimeMillis() - testStart
        logger.info(
            "Tested ${results.size} mutations in ${testTime}ms (${results.size * 1000 / maxOf(testTime, 1)} mutations/sec)",
        )

        // Merge cached results into final results so cache hits are reported
        val allResults = results + cachedResults

        // Update cache with new results
        if (enableCache && cache != null) {
            updateCache(results)
        }

        // Update test strength ordering
        if (enableTestOrdering && testStrengthOrdering != null) {
            updateTestStrength(results, filteredTestNames, killSets)
            testStrengthOrdering.flushHistory()
        }

        // Save kill sets for future subsumption analysis (merge on incremental runs)
        if (enableSubsumption && killSetStorage != null) {
            if (changedClasses.isNotEmpty()) {
                killSetStorage.saveMerged(killSets)
            } else {
                killSetStorage.save(killSets)
            }
        }

        // Post-hoc subsumption analysis (identify redundant mutations after testing)
        val finalResults =
            if (enableSubsumption && allResults.size > 10) {
                val mutations = allResults.map { it.mutation }
                val (essential, subsumed) = subsumptionAnalyzer.analyze(mutations, killSets)
                if (subsumed.isNotEmpty()) {
                    logger.info("Subsumption: ${subsumed.size} redundant mutations identified")
                    allResults.map { result ->
                        if (result.mutation.id in subsumed) {
                            result.copy(status = MutationStatus.SUBSUMED)
                        } else {
                            result
                        }
                    }
                } else {
                    allResults
                }
            } else {
                allResults
            }

        // Save baseline for future comparison (merge on incremental runs to avoid data loss)
        if (baselineStorage != null) {
            saveBaseline(finalResults, useMerge = changedClasses.isNotEmpty())
        }

        val totalTime = System.currentTimeMillis() - startTime
        return buildReport(finalResults, totalTime)
    }

    private fun generateAllMutations(classFiles: Map<String, ByteArray>): List<Pair<MutationInfo, ByteArray>> {
        val allMutations = mutableListOf<Pair<MutationInfo, ByteArray>>()
        for ((className, classBytes) in classFiles) {
            val mutations = mutator.generateMutants(classBytes)
            allMutations.addAll(mutations)
        }
        return allMutations
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
     * Get covered lines from JaCoCo execution data.
     *
     * Parses .exec files using JaCoCo API to determine which lines
     * were actually executed by tests.
     */
    private fun getCoveredLines(coverageExecFile: File): Map<String, Set<Int>> {
        if (!coverageExecFile.exists() || coverageExecFile.length() == 0L) {
            return emptyMap()
        }

        return try {
            val coveredLinesMap = coverageAnalyzer.getCoveredLines(coverageExecFile, classFilesMap)
            val totalLines = coveredLinesMap.values.flatten().size
            logger.info("Weak mutation: $totalLines covered lines across ${coveredLinesMap.size} classes")
            coveredLinesMap
        } catch (e: Exception) {
            logger.warn("Failed to parse coverage data: ${e.message}")
            emptyMap()
        }
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
                val occurrenceIndex = result.mutation.id.substringAfterLast("::").toIntOrNull() ?: 0
                cache.store(
                    classHash,
                    result.mutation.operator.operatorName,
                    result.mutation.methodName,
                    result.mutation.lineNumber,
                    occurrenceIndex,
                    result.status,
                )
            }
        }
    }

    /**
     * Update test strength ordering with results.
     * Records per-mutant outcome: only test classes that actually killed
     * the mutation get credit, not all test classes.
     */
    private fun updateTestStrength(
        results: List<MutationResult>,
        testClassNames: List<String>,
        killSets: Map<String, Set<String>>,
    ) {
        if (testStrengthOrdering == null) return

        for (result in results) {
            val killingTests = killSets[result.mutation.id] ?: emptySet()
            for (testClass in testClassNames) {
                val score = if (testClass in killingTests) 1 else 0
                testStrengthOrdering.recordResults(testClass, score, 1)
            }
        }
    }

    /**
     * Save baseline for future comparison.
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
                        Triple(
                            result.mutation.operator.operatorName,
                            result.mutation.lineNumber,
                            result.status,
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
        coverageExecFile: File,
    ): List<Pair<MutationInfo, ByteArray>> {
        val coverageData = coverageAnalyzer.loadExecutionData(coverageExecFile)
        return when (coverageData) {
            is CoverageAnalyzer.CoverageData.Empty -> {
                logger.warn("Empty coverage data, skipping coverage filtering")
                allMutations
            }
            is CoverageAnalyzer.CoverageData.Valid -> {
                val filtered = mutableListOf<Pair<MutationInfo, ByteArray>>()
                for ((mutation, mutatedBytes) in allMutations) {
                    val classBytes = classFiles[mutation.className.replace('.', '/')]
                    if (classBytes == null) {
                        // Class bytes missing from the classpath map. We can't
                        // run the JaCoCo analyzer for this class, so we err on
                        // the side of testing the mutation rather than
                        // silently dropping it — a key-mismatch here would
                        // otherwise shrink the test set for no good reason.
                        filtered.add(mutation to mutatedBytes)
                        continue
                    }
                    val coverage =
                        coverageAnalyzer.analyzeCoverage(
                            classBytes,
                            mutation.className,
                            coverageData,
                            listOf(mutation),
                        )
                    if (coverage.firstOrNull()?.coveringTests?.isNotEmpty() == true) {
                        filtered.add(mutation to mutatedBytes)
                    }
                }
                filtered
            }
        }
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

        // Pre-create BaseProjectClassLoader per target class (shared across mutations).
        val baseLoaders = mutableMapOf<String, com.github.rodrigotimoteo.mutation.classloader.BaseProjectClassLoader>()
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
                    val baseLoader = baseLoaders[mutation.className]!!
                    index to
                        executor.submit(
                            Callable {
                                runSingleMutantBatched(mutation, mutatedBytes, classFiles, baseLoader, testClassNames, testClassByteMap)
                            },
                        )
                }

            for ((index, future) in futures) {
                try {
                    val (result, failedTests) = future.get(timeoutMs, TimeUnit.MILLISECONDS)
                    results.add(result)
                    if (failedTests.isNotEmpty()) {
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
                    logger.warn("Mutation testing interrupted at mutant index $index")
                    Thread.currentThread().interrupt()
                    break
                } catch (e: Exception) {
                    logger.error("Unexpected error processing mutant: ${mutations[index].first}", e)
                    results.add(
                        MutationResult(
                            mutation = toMutation(mutations[index].first, mutatedBytes = mutations[index].second),
                            status = MutationStatus.ERROR,
                            errorMessage = e.message,
                        ),
                    )
                }
            }
        } finally {
            executor.shutdown()
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow()
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
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
            for ((targetClass, baseLoader) in baseLoaders) {
                try {
                    invokeClose(baseLoader)
                } catch (e: Exception) {
                    logger.debug("Error closing BaseProjectClassLoader for $targetClass: ${e.message}")
                }
            }
            baseLoaders.clear()
        }

        return results to killSets
    }

    /**
     * Batched mutation testing: uses shared BaseProjectClassLoader per target class.
     * Uses pre-computed mutated bytes from generateAllMutations (no re-application).
     */
    private fun runSingleMutantBatched(
        mutation: MutationInfo,
        mutatedBytes: ByteArray,
        classFiles: Map<String, ByteArray>,
        baseLoader: com.github.rodrigotimoteo.mutation.classloader.BaseProjectClassLoader,
        testClassNames: List<String>,
        testClassByteMap: Map<String, ByteArray>,
    ): Pair<MutationResult, Set<String>> {
        val startTime = System.currentTimeMillis()

        val targetClassKey = mutation.className.replace('.', '/')

        val classLoader =
            MutantClassLoaderFactory.createMutationLoader(
                baseLoader,
                targetClassKey,
                mutatedBytes,
                testClassByteMap,
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
        val results = sharedTestRunner.runTests(testClassNames, classLoader)

        if (!results.hasTests) {
            return if (results.failureMessages.isNotEmpty()) {
                MutationStatus.ERROR to emptySet()
            } else {
                MutationStatus.NO_COVERAGE to emptySet()
            }
        }

        val status =
            when {
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
     */
    private val sourceFileCache = mutableMapOf<String, List<String>?>()

    private fun findSourceCode(
        className: String,
        lineNumber: Int,
    ): Pair<String?, String?> {
        if (lineNumber <= 0) return null to null
        val relativePath = className.replace('.', '/') + ".kt"
        val javaPath = className.replace('.', '/') + ".java"
        val srcDirs = listOf("src/main/kotlin", "src/main/java", "src/commonMain/kotlin")
        for (srcDir in srcDirs) {
            for (path in listOf(relativePath, javaPath)) {
                val sourceFile = File(projectDir, "$srcDir/$path")
                val cacheKey = sourceFile.path
                if (sourceFile.exists()) {
                    // `null` cache value means "file exists but lines not
                    // yet loaded" — read once and store. A class can
                    // produce dozens of mutations; the previous code
                    // re-read the same .kt/.java from disk for every
                    // one, wasting N×file I/O.
                    val lines =
                        sourceFileCache.getOrPut(cacheKey) {
                            if (sourceFile.exists()) sourceFile.readLines() else null
                        }
                    if (lines == null) continue
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
                sourceFileCache.putIfAbsent(cacheKey, null)
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
     */
    private fun filterTestClassNames(testClassNames: List<String>): List<String> {
        if (targetTestPatterns.isEmpty() && excludeTestPatterns.isEmpty()) {
            return testClassNames
        }

        val compiledInclude = targetTestPatterns.map { Regex(it) }
        val compiledExclude = excludeTestPatterns.map { Regex(it) }

        return testClassNames.filter { name ->
            val matchesInclude = compiledInclude.isEmpty() || compiledInclude.any { it.containsMatchIn(name) }
            val matchesExclude = compiledExclude.any { it.containsMatchIn(name) }
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
