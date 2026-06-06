package com.github.rodrigotimoteo.mutation.runner

import com.github.rodrigotimoteo.mutation.classloader.MutantClassLoaderFactory
import com.github.rodrigotimoteo.mutation.model.MutationResult
import com.github.rodrigotimoteo.mutation.model.MutationStatus
import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import com.github.rodrigotimoteo.mutation.mutator.Mutator
import org.slf4j.LoggerFactory
import java.util.concurrent.Callable

/**
 * Executes tests against a single mutant using reflection-based JUnit execution.
 * Each instance runs one mutant against its covering tests.
 */
class MutantTestTask(
    private val mutation: MutationInfo,
    private val mutatedBytes: ByteArray,
    private val classFiles: Map<String, ByteArray>,
    private val testClassNames: List<String>,
    private val parentClassLoader: ClassLoader,
) : Callable<MutationResult> {
    private val logger = LoggerFactory.getLogger(MutantTestTask::class.java)

    override fun call(): MutationResult {
        val startTime = System.currentTimeMillis()

        return try {
            // Pass original classFiles to classloader — it applies the single mutation
            val mutator = Mutator(setOf(mutation.operator))
            val classLoader =
                MutantClassLoaderFactory.create(
                    parentClassLoader,
                    classFiles,
                    mutation,
                    mutator,
                )

            // Run tests with mutant classloader
            val status = runTests(classLoader, testClassNames)

            MutationResult(
                mutation = toMutation(mutation),
                status = status,
                executionTimeMs = System.currentTimeMillis() - startTime,
            )
        } catch (e: Throwable) {
            logger.debug("Mutant execution error: ${e.message}", e)
            MutationResult(
                mutation = toMutation(mutation),
                status = MutationStatus.ERROR,
                executionTimeMs = System.currentTimeMillis() - startTime,
                errorMessage = e.message,
            )
        }
    }

    private fun runTests(
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
                    } catch (ite: java.lang.reflect.InvocationTargetException) {
                        // Any exception from test execution = mutation detected.
                        hasFailures = true
                        logger.debug("Test failed: ${method.name}", ite.targetException)
                    } catch (e: Exception) {
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

    private fun toMutation(info: MutationInfo): com.github.rodrigotimoteo.mutation.model.Mutation {
        return com.github.rodrigotimoteo.mutation.model.Mutation(
            id = "${info.operator.operatorName}::${info.className}::${info.methodName}::${info.lineNumber}",
            className = info.className,
            methodName = info.methodName,
            methodDescriptor = info.methodDescriptor,
            operator = info.operator,
            lineNumber = info.lineNumber,
            originalBytecode = ByteArray(0),
            mutatedBytecode = ByteArray(0),
            description = info.description,
        )
    }
}
