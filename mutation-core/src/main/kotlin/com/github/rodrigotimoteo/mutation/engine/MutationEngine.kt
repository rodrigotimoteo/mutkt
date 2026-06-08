package com.github.rodrigotimoteo.mutation.engine

import com.github.rodrigotimoteo.mutation.analysis.KillSetStorage
import com.github.rodrigotimoteo.mutation.analysis.SubsumptionAnalyzer
import com.github.rodrigotimoteo.mutation.analysis.TestStrengthOrdering
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
    private val timeoutMs: Long = 30000,
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
) {
    private val logger = LoggerFactory.getLogger(MutationEngine::class.java)
    private val mutator = Mutator(enabledOperators)
    private val coverageAnalyzer = CoverageAnalyzer()
    private val subsumptionAnalyzer = SubsumptionAnalyzer()
    private val killSetStorage = projectDir?.let { KillSetStorage(it) }
    private val testStrengthOrdering = projectDir?.let { TestStrengthOrdering(it) }
    private val cache = projectDir?.let { MutKtCache(it) }
    private val baselineStorage = projectDir?.let { BaselineStorage(it) }

    /** 0 = use all available processors. */
    private val parallelism = if (maxParallelMutants > 0) maxParallelMutants else Runtime.getRuntime().availableProcessors()

    // Compiled regex patterns for include/exclude filtering
    private val compiledIncludePatterns = includePatterns.map { Regex(it) }
    private val compiledExcludePatterns = excludePatterns.map { Regex(it) }

    // Store class files for cache lookups
    private var classFilesMap: Map<String, ByteArray> = emptyMap()

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

        val startTime = System.currentTimeMillis()
        val scanStart = System.currentTimeMillis()
        val allMutations = generateAllMutations(classFiles)
        val scanTime = System.currentTimeMillis() - scanStart
        System.err.println("[MutKt] Scanned ${allMutations.size} mutations in ${scanTime}ms")

        // Combine class files with test class files
        val allClassFiles = classFiles + testClassBytes

        // Filter by regex patterns (include/exclude)
        val mutationsAfterRegex = filterByPatterns(allMutations)
        val skippedRegex = allMutations.size - mutationsAfterRegex.size
        if (skippedRegex > 0) {
            System.err.println(
                "[MutKt] Regex: skipped $skippedRegex mutations (include=${includePatterns.size}, exclude=${excludePatterns.size})",
            )
        }

        // Filter mutations by coverage if available
        val mutationsAfterCoverage =
            if (coverageExecFile != null && coverageExecFile.exists()) {
                filterByCoverage(mutationsAfterRegex, classFiles, coverageExecFile)
            } else {
                mutationsAfterRegex
            }
        logger.info("Testing ${mutationsAfterCoverage.size} mutations after coverage filtering")

        // Weak mutation analysis (skip unreachable mutations)
        val mutationsAfterWeak =
            if (enableWeakMutation && coverageExecFile != null && coverageExecFile.exists()) {
                val coveredLinesMap = getCoveredLines(coverageExecFile)
                val (reachable, unreachable) =
                    mutationsAfterCoverage.partition { (mutation, _) ->
                        val classLines = coveredLinesMap[mutation.className]
                        classLines != null && mutation.lineNumber in classLines
                    }
                if (unreachable.isNotEmpty()) {
                    System.err.println("[MutKt] Weak mutation: skipped ${unreachable.size} unreachable mutations")
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
        val orderedTestNames =
            if (enableTestOrdering && testStrengthOrdering != null) {
                testStrengthOrdering.orderTests(testClassNames)
            } else {
                testClassNames
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
                    System.err.println(
                        "[MutKt] Incremental: skipped $skippedIncremental mutations (only testing ${changedClasses.size} changed classes)",
                    )
                }
                filtered
            } else {
                mutationsAfterInlined
            }

        // Pre-test subsumption: use historical kill sets to skip likely-subsumed mutants
        val mutationsAfterSubsumption: List<Pair<MutationInfo, ByteArray>>
        val predictedSubsumed: Set<String>
        if (enableSubsumption && killSetStorage != null && mutationsAfterIncremental.size > 10) {
            val historicalKillSets = killSetStorage.load()
            if (historicalKillSets.isNotEmpty()) {
                val mutations = mutationsAfterIncremental.map { toMutation(it.first) }
                val subsumed = subsumptionAnalyzer.predictSubsumed(mutations, historicalKillSets)
                if (subsumed.isNotEmpty()) {
                    System.err.println("[MutKt] Subsumption (pre-test): ${subsumed.size} likely redundant mutations will be skipped")
                    mutationsAfterSubsumption =
                        mutationsAfterIncremental.filter { (info, _) ->
                            val id = "${info.operator.operatorName}::${info.className}::${info.methodName}::${info.lineNumber}"
                            id !in subsumed
                        }
                    predictedSubsumed = subsumed
                } else {
                    mutationsAfterSubsumption = mutationsAfterIncremental
                    predictedSubsumed = emptySet()
                }
            } else {
                mutationsAfterSubsumption = mutationsAfterIncremental
                predictedSubsumed = emptySet()
            }
        } else {
            mutationsAfterSubsumption = mutationsAfterIncremental
            predictedSubsumed = emptySet()
        }

        // Check cache for previously tested mutations
        val (mutationsToTest, cachedResults) = filterByCache(mutationsAfterSubsumption)
        if (cachedResults.isNotEmpty()) {
            System.err.println("[MutKt] Cache: ${cachedResults.size} mutations already tested")
        }

        // Run tests against each mutant
        val testStart = System.currentTimeMillis()
        val (results, killSets) = runMutants(mutationsToTest, allClassFiles, orderedTestNames, testClassLoader)
        val testTime = System.currentTimeMillis() - testStart
        System.err.println(
            "[MutKt] Tested ${results.size} mutations in ${testTime}ms (${results.size * 1000 / maxOf(testTime, 1)} mutations/sec)",
        )

        // Update cache with new results
        if (enableCache && cache != null) {
            updateCache(results)
        }

        // Update test strength ordering
        if (enableTestOrdering && testStrengthOrdering != null) {
            updateTestStrength(results, testClassNames, killSets)
            testStrengthOrdering.flushHistory()
        }

        // Verify pre-test subsumption and re-run if needed
        val verifiedResults =
            if (enableSubsumption && predictedSubsumed.isNotEmpty() && killSetStorage != null) {
                verifyAndRerunSubsumption(
                    results,
                    predictedSubsumed,
                    killSets,
                    mutationsAfterIncremental,
                    allClassFiles,
                    orderedTestNames,
                    testClassLoader,
                )
            } else {
                results
            }

        // Save kill sets for future pre-test subsumption
        if (enableSubsumption && killSetStorage != null) {
            killSetStorage.save(killSets)
        }

        // Post-hoc subsumption analysis (for mutations that weren't pre-filtered)
        val finalResults =
            if (enableSubsumption && verifiedResults.size > 10) {
                val mutations = verifiedResults.map { it.mutation }
                val (essential, subsumed) = subsumptionAnalyzer.analyze(mutations, killSets)
                if (subsumed.isNotEmpty()) {
                    System.err.println("[MutKt] Subsumption (post-test): ${subsumed.size} redundant mutations identified")
                    verifiedResults.map { result ->
                        if (result.mutation.id in subsumed) {
                            result.copy(status = MutationStatus.SUBSUMED)
                        } else {
                            result
                        }
                    }
                } else {
                    verifiedResults
                }
            } else {
                verifiedResults
            }

        // Save baseline for future comparison
        if (baselineStorage != null) {
            saveBaseline(finalResults)
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
                val cachedStatus = cache.lookup(classHash, mutation.operator.operatorName, mutation.lineNumber)
                if (cachedStatus != null) {
                    cached.add(
                        MutationResult(
                            mutation = toMutation(mutation),
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
                    result.mutation.lineNumber,
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
     * Verify pre-test subsumption predictions and re-run incorrectly skipped mutants.
     *
     * Compares historical kill sets with actual kill sets. If a mutation was predicted
     * as subsumed but was actually killed by a test, re-run it to get correct results.
     */
    private fun verifyAndRerunSubsumption(
        results: List<MutationResult>,
        predictedSubsumed: Set<String>,
        actualKillSets: Map<String, Set<String>>,
        allMutations: List<Pair<MutationInfo, ByteArray>>,
        classFiles: Map<String, ByteArray>,
        testClassNames: List<String>,
        testClassLoader: ClassLoader?,
    ): List<MutationResult> {
        // Find mutations that were predicted as subsumed but were actually killed
        val incorrectlySubsumed =
            predictedSubsumed.filter { mutationId ->
                val killingTests = actualKillSets[mutationId]
                killingTests != null && killingTests.isNotEmpty()
            }

        if (incorrectlySubsumed.isEmpty()) {
            System.err.println("[MutKt] Subsumption verification: all predictions correct")
            return results
        }

        System.err.println(
            "[MutKt] Subsumption verification: ${incorrectlySubsumed.size} mutations were incorrectly predicted as subsumed, re-running",
        )

        // Find the mutations to re-run
        val toRerun =
            allMutations.filter { (info, _) ->
                val mutationId = "${info.operator.operatorName}::${info.className}::${info.methodName}::${info.lineNumber}"
                mutationId in incorrectlySubsumed
            }

        if (toRerun.isEmpty()) {
            System.err.println("[MutKt] Subsumption verification: could not find mutations to re-run")
            return results
        }

        // Re-run the incorrectly subsumed mutations
        val (rerunResults, _) = runMutants(toRerun, classFiles, testClassNames, testClassLoader)

        // Merge results: replace with actual results
        val rerunById = rerunResults.associateBy { it.mutation.id }

        return results.map { result ->
            if (result.mutation.id in rerunById) {
                rerunById[result.mutation.id]!!
            } else {
                result
            }
        }
    }

    /**
     * Save baseline for future comparison.
     */
    private fun saveBaseline(results: List<MutationResult>) {
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

        baselineStorage.save(baselineData)
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
        if (coverageData.empty) {
            logger.warn("Empty coverage data, skipping coverage filtering")
            return allMutations
        }

        val filtered = mutableListOf<Pair<MutationInfo, ByteArray>>()

        for ((mutation, mutatedBytes) in allMutations) {
            val classBytes = classFiles[mutation.className.replace('.', '/')]
            if (classBytes != null) {
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

        // Reset the shared class-loading failure cache for this run.
        MutantClassLoaderFactory.resetCache()

        val parentLoader = testClassLoader ?: this.javaClass.classLoader
        val testClassByteMap =
            classFiles.filterKeys { key ->
                testClassNames.any { it.replace('.', '/') == key }
            }

        // Group mutations by target class. Each group shares a BaseProjectClassLoader
        // that loads all non-target project classes once, avoiding repeated defineClass.
        val mutationsByClass = mutations.groupBy { it.first.className }
        System.err.println("[MutKt] ${mutations.size} mutations across ${mutationsByClass.size} classes")

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
        val executor: ExecutorService =
            Executors.newFixedThreadPool(parallelism) { runnable ->
                Thread(runnable, "mutation-worker").apply { isDaemon = true }
            }
        System.err.println("[MutKt] Running $parallelism parallel workers")

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
            )

        val (status, failedTestClasses) = runTestsWithClassLoader(classLoader, testClassNames)

        return MutationResult(
            mutation = toMutation(mutation),
            status = status,
            executionTimeMs = System.currentTimeMillis() - startTime,
        ) to failedTestClasses
    }

    private fun runTestsWithClassLoader(
        classLoader: ClassLoader,
        testClassNames: List<String>,
    ): Pair<MutationStatus, Set<String>> {
        // Use ReflectionTestRunner which supports @Nested, @ParameterizedTest, @RepeatedTest
        val runner = ReflectionTestRunner(classLoader)
        val results = runner.runTests(testClassNames)

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
        originalBytes: ByteArray = ByteArray(0),
        mutatedBytes: ByteArray = ByteArray(0),
    ): Mutation {
        return Mutation(
            id = "${info.operator.operatorName}::${info.className}::${info.methodName}::${info.lineNumber}",
            className = info.className,
            methodName = info.methodName,
            methodDescriptor = info.methodDescriptor,
            operator = info.operator,
            lineNumber = info.lineNumber,
            originalBytecode = originalBytes,
            mutatedBytecode = mutatedBytes,
            description = info.description,
        )
    }

    private fun buildReport(
        results: List<MutationResult>,
        totalTime: Long,
    ): MutationReport {
        val killed = results.count { it.isKilled }
        val survived = results.count { it.isSurvived }
        val errors = results.count { it.status == MutationStatus.ERROR }
        val timeouts = results.count { it.status == MutationStatus.TIMEOUT }
        val noCoverage = results.count { it.status == MutationStatus.NO_COVERAGE }

        return MutationReport(
            results = results,
            totalMutations = results.size,
            killedMutations = killed,
            survivedMutations = survived,
            errorMutations = errors,
            timeoutMutations = timeouts,
            noCoverageMutations = noCoverage,
            totalExecutionTimeMs = totalTime,
        )
    }
}
