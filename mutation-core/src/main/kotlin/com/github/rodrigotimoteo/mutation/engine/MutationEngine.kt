package com.github.rodrigotimoteo.mutation.engine

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
 * Example:
 * ```kotlin
 * val engine = MutationEngine(
 *     enabledOperators = MutationOperator.MVP_OPERATORS,
 *     timeoutMs = 30000,
 *     maxParallelMutants = 4,
 * )
 * val report = engine.runMutationTesting(classFiles, testClassNames)
 * println("Score: ${report.killedPercentage}%")
 * ```
 *
 * @property enabledOperators Mutation operators to apply
 * @property timeoutMs Timeout per mutant in milliseconds
 * @property maxParallelMutants Number of parallel mutant executions
 * @see MutationReport for result details
 */
class MutationEngine(
    private val enabledOperators: Set<MutationOperator> = MutationOperator.MVP_OPERATORS,
    private val timeoutMs: Long = 30000,
    maxParallelMutants: Int = 0,
    private val enableInlinedFinally: Boolean = true,
    private val enableTestOrdering: Boolean = true,
) {
    private val logger = LoggerFactory.getLogger(MutationEngine::class.java)
    private val mutator = Mutator(enabledOperators)
    private val coverageAnalyzer = CoverageAnalyzer()

    /** 0 = use all available processors. */
    private val parallelism = if (maxParallelMutants > 0) maxParallelMutants else Runtime.getRuntime().availableProcessors()

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
        val startTime = System.currentTimeMillis()
        val scanStart = System.currentTimeMillis()
        val allMutations = generateAllMutations(classFiles)
        val scanTime = System.currentTimeMillis() - scanStart
        System.err.println("[MutKt] Scanned ${allMutations.size} mutations in ${scanTime}ms")

        // Combine class files with test class files
        val allClassFiles = classFiles + testClassBytes

        // Filter mutations by coverage if available
        val mutationsAfterCoverage =
            if (coverageExecFile != null && coverageExecFile.exists()) {
                filterByCoverage(allMutations, classFiles, coverageExecFile)
            } else {
                allMutations
            }
        logger.info("Testing ${mutationsAfterCoverage.size} mutations after coverage filtering")

        // Filter out mutations in inlined finally blocks (duplicates)
        val mutationsAfterInlined =
            if (enableInlinedFinally) {
                val detector = InlinedFinallyDetector()
                mutationsAfterCoverage.filter { (mutation, _) ->
                    val classBytes = classFiles[mutation.className.replace('.', '/')]
                    if (classBytes != null) {
                        val blocks = detector.detect(classBytes)
                        !detector.isInInlinedBlock(mutation.lineNumber, blocks)
                    } else {
                        true
                    }
                }
            } else {
                mutationsAfterCoverage
            }
        val skippedInlined = mutationsAfterCoverage.size - mutationsAfterInlined.size
        if (skippedInlined > 0) {
            logger.info("Skipped $skippedInlined mutations in inlined finally blocks")
        }

        // Order test classes by effectiveness (if history available)
        val orderedTestNames =
            if (enableTestOrdering) {
                TestOrderingStrategy().orderTests(testClassNames)
            } else {
                testClassNames
            }

        // Run tests against each mutant
        val testStart = System.currentTimeMillis()
        val results = runMutants(mutationsAfterInlined, allClassFiles, orderedTestNames, testClassLoader)
        val testTime = System.currentTimeMillis() - testStart
        System.err.println(
            "[MutKt] Tested ${results.size} mutations in ${testTime}ms (${results.size * 1000 / maxOf(testTime, 1)} mutations/sec)",
        )

        // Subsumption analysis disabled until per-test kill attribution is available.
        // Currently className is used as kill-set proxy, producing nonsensical results.
        // When MutationResult carries killing test identifiers, re-enable:
        // val subsumptionResult = SubsumptionAnalyzer().analyze(results)

        val totalTime = System.currentTimeMillis() - startTime
        return buildReport(results, totalTime)
    }

    private fun generateAllMutations(classFiles: Map<String, ByteArray>): List<Pair<MutationInfo, ByteArray>> {
        val allMutations = mutableListOf<Pair<MutationInfo, ByteArray>>()
        for ((className, classBytes) in classFiles) {
            val mutations = mutator.generateMutants(classBytes)
            allMutations.addAll(mutations)
        }
        return allMutations
    }

    private fun filterByCoverage(
        allMutations: List<Pair<MutationInfo, ByteArray>>,
        classFiles: Map<String, ByteArray>,
        coverageExecFile: File,
    ): List<Pair<MutationInfo, ByteArray>> {
        val executionData = coverageAnalyzer.loadExecutionData(coverageExecFile)
        if (executionData.isEmpty()) {
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
                        executionData,
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
    ): List<MutationResult> {
        if (mutations.isEmpty()) return emptyList()

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
                    val result = future.get(timeoutMs, TimeUnit.MILLISECONDS) as MutationResult
                    results.add(result)
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

        return results
    }

    /**
     * Batched mutation testing: uses shared BaseProjectClassLoader per target class.
     * Only defines mutated target + test classes per mutation (not all project classes).
     */
    private fun runSingleMutantBatched(
        mutation: MutationInfo,
        mutatedBytes: ByteArray,
        classFiles: Map<String, ByteArray>,
        baseLoader: com.github.rodrigotimoteo.mutation.classloader.BaseProjectClassLoader,
        testClassNames: List<String>,
        testClassByteMap: Map<String, ByteArray>,
    ): MutationResult {
        val startTime = System.currentTimeMillis()

        val targetClassKey = mutation.className.replace('.', '/')
        val originalClassBytes = classFiles[targetClassKey]
        val preMutatedBytes =
            if (originalClassBytes != null) {
                mutator.applyMutation(originalClassBytes, mutation)
            } else {
                mutatedBytes
            }

        val classLoader =
            MutantClassLoaderFactory.createMutationLoader(
                baseLoader,
                targetClassKey,
                preMutatedBytes,
                testClassByteMap,
            )

        val status = runTestsWithClassLoader(classLoader, testClassNames)

        return MutationResult(
            mutation = toMutation(mutation),
            status = status,
            executionTimeMs = System.currentTimeMillis() - startTime,
        )
    }

    /**
     * Legacy single-mutant path (used by tests that don't group by class).
     */
    private fun runSingleMutant(
        mutation: MutationInfo,
        mutatedBytes: ByteArray,
        classFiles: Map<String, ByteArray>,
        testClassNames: List<String>,
        testClassLoader: ClassLoader? = null,
    ): MutationResult {
        val startTime = System.currentTimeMillis()

        val targetClassKey = mutation.className.replace('.', '/')
        val originalClassBytes = classFiles[targetClassKey]
        val preMutatedBytes =
            if (originalClassBytes != null) {
                mutator.applyMutation(originalClassBytes, mutation)
            } else {
                mutatedBytes
            }

        val parentLoader = testClassLoader ?: this.javaClass.classLoader
        val classLoader =
            MutantClassLoaderFactory.create(
                parentLoader,
                classFiles,
                mutation,
                mutator,
                preMutatedBytes,
            )

        val status = runTestsWithClassLoader(classLoader, testClassNames)

        return MutationResult(
            mutation = toMutation(mutation),
            status = status,
            executionTimeMs = System.currentTimeMillis() - startTime,
        )
    }

    private fun runTestsWithClassLoader(
        classLoader: ClassLoader,
        testClassNames: List<String>,
    ): MutationStatus {
        var hasTests = false
        var hasFailures = false
        var hasErrors = false

        for (testClassName in testClassNames) {
            try {
                val testClass = classLoader.loadClass(testClassName)
                val testMethods =
                    testClass.declaredMethods.filter {
                        it.getAnnotation(org.junit.jupiter.api.Test::class.java) != null
                    }

                // Discover lifecycle methods (JUnit 5).
                val beforeEachMethods =
                    testClass.declaredMethods.filter {
                        it.isAnnotationPresent(org.junit.jupiter.api.BeforeEach::class.java)
                    }
                val afterEachMethods =
                    testClass.declaredMethods.filter {
                        it.isAnnotationPresent(org.junit.jupiter.api.AfterEach::class.java)
                    }

                for (method in testMethods) {
                    hasTests = true
                    val instance = testClass.getDeclaredConstructor().newInstance()
                    try {
                        // Run @BeforeEach setup methods.
                        for (setup in beforeEachMethods) {
                            setup.isAccessible = true
                            setup.invoke(instance)
                        }
                        method.invoke(instance)
                    } catch (e: java.lang.reflect.InvocationTargetException) {
                        val cause = e.targetException
                        // Any exception from test execution (assertion, NPE, AIOOBE, etc.)
                        // means the mutation is detected — the test suite caught the broken code.
                        hasFailures = true
                        logger.debug("Test failed (mutation killed): ${method.name}", cause)
                        // Early termination: mutation is killed, no need to run more tests.
                        return MutationStatus.KILLED
                    } catch (e: Exception) {
                        // Non-invocation exceptions (e.g. instantiate failure, class not found)
                        // indicate infrastructure issues, not mutation detection.
                        hasErrors = true
                        logger.debug("Test error: ${method.name}", e)
                    } finally {
                        // Always run @AfterEach teardown methods.
                        for (teardown in afterEachMethods) {
                            try {
                                teardown.isAccessible = true
                                teardown.invoke(instance)
                            } catch (_: Exception) {
                                // Teardown failure doesn't mask test result.
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.debug("Could not load test class: $testClassName", e)
                hasErrors = true
            }
        }

        return when {
            !hasTests && hasErrors -> MutationStatus.ERROR
            !hasTests -> MutationStatus.NO_COVERAGE
            hasFailures -> MutationStatus.KILLED
            hasErrors -> MutationStatus.ERROR
            else -> MutationStatus.SURVIVED
        }
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
