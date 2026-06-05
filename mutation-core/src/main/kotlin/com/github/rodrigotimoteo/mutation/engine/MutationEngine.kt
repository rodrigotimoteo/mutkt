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
import com.github.rodrigotimoteo.mutation.report.SubsumptionAnalyzer
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
    private val maxParallelMutants: Int = 4,
) {
    private val logger = LoggerFactory.getLogger(MutationEngine::class.java)
    private val mutator = Mutator(enabledOperators)
    private val coverageAnalyzer = CoverageAnalyzer()

    /**
     * Runs mutation testing on the given classpath.
     */
    fun runMutationTesting(
        classFiles: Map<String, ByteArray>,
        testClassNames: List<String>,
        testClassBytes: Map<String, ByteArray> = emptyMap(),
        coverageExecFile: File? = null,
    ): MutationReport {
        val startTime = System.currentTimeMillis()
        val allMutations = generateAllMutations(classFiles)
        logger.info("Generated ${allMutations.size} mutations")

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
        val detector = InlinedFinallyDetector()
        val mutationsAfterInlined =
            mutationsAfterCoverage.filter { (mutation, _) ->
                // Check if this mutation is in an inlined finally block
                val classBytes = classFiles[mutation.className.replace('.', '/')]
                if (classBytes != null) {
                    val blocks = detector.detect(classBytes)
                    !detector.isInInlinedBlock(mutation.lineNumber, blocks)
                } else {
                    true
                }
            }
        val skippedInlined = mutationsAfterCoverage.size - mutationsAfterInlined.size
        if (skippedInlined > 0) {
            logger.info("Skipped $skippedInlined mutations in inlined finally blocks")
        }

        // Order test classes by effectiveness (if history available)
        val orderingStrategy = TestOrderingStrategy()
        val orderedTestNames = orderingStrategy.orderTests(testClassNames)

        // Run tests against each mutant
        val results = runMutants(mutationsAfterInlined, allClassFiles, orderedTestNames)

        // Analyze subsumption
        val subsumptionAnalyzer = SubsumptionAnalyzer()
        val subsumptionResult = subsumptionAnalyzer.analyze(results)
        if (subsumptionResult.skippedCount > 0) {
            logger.info("Subsumption analysis: ${subsumptionResult.skippedCount} mutations subsumed")
        }

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
    ): List<MutationResult> {
        if (mutations.isEmpty()) return emptyList()

        val results = mutableListOf<MutationResult>()
        val executor: ExecutorService =
            Executors.newFixedThreadPool(maxParallelMutants) { runnable ->
                Thread(runnable, "mutation-worker").apply { isDaemon = true }
            }

        try {
            val futures =
                mutations.mapIndexed { index, (mutation, mutatedBytes) ->
                    index to
                        executor.submit(
                            Callable {
                                runSingleMutant(mutation, mutatedBytes, classFiles, testClassNames)
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

    private fun runSingleMutant(
        mutation: MutationInfo,
        mutatedBytes: ByteArray,
        classFiles: Map<String, ByteArray>,
        testClassNames: List<String>,
    ): MutationResult {
        val startTime = System.currentTimeMillis()

        // Don't pre-mutate — let MutantClassLoader apply the mutation
        val classLoader =
            MutantClassLoaderFactory.create(
                this.javaClass.classLoader,
                classFiles,
                mutation,
                mutator,
            )

        // Run tests with mutant classloader
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

                for (method in testMethods) {
                    hasTests = true
                    val instance = testClass.getDeclaredConstructor().newInstance()
                    try {
                        method.invoke(instance)
                    } catch (e: java.lang.reflect.InvocationTargetException) {
                        val cause = e.targetException
                        if (cause is AssertionError) {
                            hasFailures = true
                            logger.debug("Test failed (mutation killed): ${method.name}", cause)
                        } else {
                            hasErrors = true
                            logger.debug("Test error: ${method.name}", cause)
                        }
                    } catch (e: Exception) {
                        hasFailures = true
                        logger.debug("Test failed: ${method.name}", e)
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
            id = "${info.operator.operatorName}_${info.className}_${info.methodName}_${info.lineNumber}",
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
