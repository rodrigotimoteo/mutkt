package com.github.rodrigotimoteo.mutation.engine

import com.github.rodrigotimoteo.mutation.coverage.CoverageAnalyzer
import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import com.github.rodrigotimoteo.mutation.mutator.Mutator
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import com.github.rodrigotimoteo.mutation.model.Mutation
import com.github.rodrigotimoteo.mutation.model.MutationReport
import com.github.rodrigotimoteo.mutation.model.MutationResult
import com.github.rodrigotimoteo.mutation.model.MutationStatus
import com.github.rodrigotimoteo.mutation.classloader.MutantClassLoaderFactory
import org.slf4j.LoggerFactory

import java.io.File
import java.lang.reflect.Method
import java.net.URLClassLoader
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Main mutation testing engine.
 * Orchestrates mutation generation, test execution, and result aggregation.
 */
class MutationEngine(
    private val enabledOperators: Set<MutationOperator> = MutationOperator.MVP_OPERATORS,
    private val timeoutMs: Long = 30000,
    private val maxParallelMutants: Int = 4
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
        coverageExecFile: File? = null
    ): MutationReport {
        val startTime = System.currentTimeMillis()
        val allMutations = generateAllMutations(classFiles)
        logger.info("Generated ${allMutations.size} mutations")

        // Combine class files with test class files
        val allClassFiles = classFiles + testClassBytes

        // Filter mutations by coverage if available
        val mutationsToTest = if (coverageExecFile != null && coverageExecFile.exists()) {
            filterByCoverage(allMutations, classFiles, coverageExecFile)
        } else {
            allMutations
        }
        logger.info("Testing ${mutationsToTest.size} mutations after coverage filtering")

        // Run tests against each mutant
        val results = runMutants(mutationsToTest, allClassFiles, testClassNames)

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
        coverageExecFile: File
    ): List<Pair<MutationInfo, ByteArray>> {
        val executionData = coverageAnalyzer.loadExecutionData(coverageExecFile)
        val filtered = mutableListOf<Pair<MutationInfo, ByteArray>>()

        for ((mutation, mutatedBytes) in allMutations) {
            val classBytes = classFiles[mutation.className.replace('.', '/')]
            if (classBytes != null) {
                val coverage = coverageAnalyzer.analyzeCoverage(
                    classBytes,
                    mutation.className,
                    executionData,
                    listOf(mutation)
                )
                if (coverage.first().coveringTests.isNotEmpty()) {
                    filtered.add(mutation to mutatedBytes)
                }
            }
        }
        return filtered
    }

    private fun runMutants(
        mutations: List<Pair<MutationInfo, ByteArray>>,
        classFiles: Map<String, ByteArray>,
        testClassNames: List<String>
    ): List<MutationResult> {
        val results = mutableListOf<MutationResult>()
        val executor: ExecutorService = Executors.newFixedThreadPool(maxParallelMutants)

        try {
            val futures = mutations.map { (mutation, mutatedBytes) ->
                executor.submit(Callable {
                    runSingleMutant(mutation, mutatedBytes, classFiles, testClassNames)
                })
            }

            for (future in futures) {
                try {
                    val result = future.get(timeoutMs, TimeUnit.MILLISECONDS) as MutationResult
                    results.add(result)
                } catch (e: Exception) {
                    logger.error("Mutant execution failed", e)
                    val mutation = mutations[futures.indexOf(future)].first
                    results.add(MutationResult(
                        mutation = toMutation(mutation),
                        status = MutationStatus.ERROR,
                        errorMessage = e.message
                    ))
                }
            }
        } finally {
            executor.shutdown()
            executor.awaitTermination(10, TimeUnit.SECONDS)
        }

        return results
    }

    private fun runSingleMutant(
        mutation: MutationInfo,
        mutatedBytes: ByteArray,
        classFiles: Map<String, ByteArray>,
        testClassNames: List<String>
    ): MutationResult {
        val startTime = System.currentTimeMillis()

        // Create classloader with mutated class
        val mutatedClassFiles = classFiles.toMutableMap()
        mutatedClassFiles[mutation.className.replace('.', '/')] = mutatedBytes

        val classLoader = MutantClassLoaderFactory.create(
            this.javaClass.classLoader,
            mutatedClassFiles,
            mutation,
            mutator
        )

        // Run tests with mutant classloader
        val status = try {
            runTestsWithClassLoader(classLoader, testClassNames)
        } catch (e: Exception) {
            return MutationResult(
                mutation = toMutation(mutation),
                status = MutationStatus.ERROR,
                executionTimeMs = System.currentTimeMillis() - startTime,
                errorMessage = e.message
            )
        }

        return MutationResult(
            mutation = toMutation(mutation),
            status = status,
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }

    private fun runTestsWithClassLoader(
        classLoader: ClassLoader,
        testClassNames: List<String>
    ): MutationStatus {
        // Simplified test execution - in real implementation use JUnit Platform
        // For MVP, we'll use reflection to run test methods
        var hasTests = false
        var hasFailures = false

        for (testClassName in testClassNames) {
            try {
                val testClass = classLoader.loadClass(testClassName)
                val testMethods = testClass.declaredMethods.filter {
                    it.getAnnotation(org.junit.jupiter.api.Test::class.java) != null
                }
                
                for (method in testMethods) {
                    hasTests = true
                    val instance = testClass.getDeclaredConstructor().newInstance()
                    try {
                        method.invoke(instance)
                    } catch (e: Exception) {
                        hasFailures = true
                        logger.debug("Test failed: ${method.name}", e)
                    }
                }
            } catch (e: Exception) {
                logger.debug("Could not load test class: $testClassName", e)
            }
        }

        return if (!hasTests) {
            MutationStatus.NO_COVERAGE
        } else if (hasFailures) {
            MutationStatus.KILLED
        } else {
            MutationStatus.SURVIVED
        }
    }

    private fun toMutation(info: MutationInfo): Mutation {
        return Mutation(
            id = "${info.operator.operatorName}_${info.className}_${info.methodName}_${info.lineNumber}",
            className = info.className,
            methodName = info.methodName,
            methodDescriptor = info.methodDescriptor,
            operator = info.operator,
            lineNumber = info.lineNumber,
            originalBytecode = ByteArray(0),
            mutatedBytecode = ByteArray(0),
            description = info.description
        )
    }

    private fun buildReport(results: List<MutationResult>, totalTime: Long): MutationReport {
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
            totalExecutionTimeMs = totalTime
        )
    }
}