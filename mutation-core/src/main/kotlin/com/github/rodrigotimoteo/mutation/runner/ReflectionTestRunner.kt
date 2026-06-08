package com.github.rodrigotimoteo.mutation.runner

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.slf4j.LoggerFactory

/**
 * Test runner that handles JUnit 5 features via reflection.
 *
 * Supports:
 * - @Test (standard tests)
 * - @BeforeEach/@AfterEach (lifecycle)
 * - @Nested (inner test classes)
 * - @ParameterizedTest (parameterized tests via @MethodSource, @ValueSource, etc.)
 * - @RepeatedTest (repeated tests)
 *
 * This is the primary runner. JUnit Platform Launcher integration is optional
 * and handled separately when available.
 */
class ReflectionTestRunner(
    private val classLoader: ClassLoader,
) {
    private val logger = LoggerFactory.getLogger(ReflectionTestRunner::class.java)

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

    fun runTests(testClassNames: List<String>): TestResults {
        var testsFound = 0
        var testsSucceeded = 0
        var testsFailed = 0
        val failures = mutableListOf<String>()
        val failedTestClasses = mutableSetOf<String>()

        for (testClassName in testClassNames) {
            try {
                val testClass = classLoader.loadClass(testClassName)
                val results = runTestClass(testClass, testClassName)
                testsFound += results.first
                testsSucceeded += results.second
                testsFailed += results.third
                failures.addAll(results.fourth)
                if (results.third > 0) {
                    failedTestClasses.add(testClassName)
                }
            } catch (e: Exception) {
                logger.warn("Could not load test class: $testClassName — ${e.message}")
            }
        }

        return TestResults(testsFound, testsSucceeded, testsFailed, 0, failures, failedTestClasses)
    }

    /**
     * Runs a single test class, including @Nested inner classes.
     */
    private fun runTestClass(
        testClass: Class<*>,
        className: String,
    ): TestClassResult {
        var testsFound = 0
        var testsSucceeded = 0
        var testsFailed = 0
        val failures = mutableListOf<String>()

        // Discover test methods (@Test, @ParameterizedTest, @RepeatedTest)
        val testMethods =
            testClass.declaredMethods.filter { method ->
                method.isAnnotationPresent(Test::class.java) ||
                    method.isAnnotationPresent(ParameterizedTest::class.java) ||
                    method.isAnnotationPresent(RepeatedTest::class.java)
            }

        // Discover lifecycle methods
        val beforeEachMethods =
            testClass.declaredMethods.filter {
                it.isAnnotationPresent(BeforeEach::class.java)
            }
        val afterEachMethods =
            testClass.declaredMethods.filter {
                it.isAnnotationPresent(AfterEach::class.java)
            }

        // Run each test method
        for (method in testMethods) {
            if (method.isAnnotationPresent(ParameterizedTest::class.java)) {
                // Handle @ParameterizedTest — invoke multiple times with different arguments
                val paramResults = runParameterizedTest(testClass, method, beforeEachMethods, afterEachMethods)
                testsFound += paramResults.first
                testsSucceeded += paramResults.second
                testsFailed += paramResults.third
                failures.addAll(paramResults.fourth)
            } else {
                // Standard @Test or @RepeatedTest
                val repeatedAnnotation = method.getAnnotation(RepeatedTest::class.java)
                val repetitions = repeatedAnnotation?.value ?: 1
                testsFound += repetitions
                for (rep in 1..repetitions) {
                    val instance = testClass.getDeclaredConstructor().newInstance()
                    try {
                        for (setup in beforeEachMethods) {
                            setup.isAccessible = true
                            setup.invoke(instance)
                        }
                        method.invoke(instance)
                        testsSucceeded++
                    } catch (e: java.lang.reflect.InvocationTargetException) {
                        testsFailed++
                        failures.add("$className.${method.name}: ${e.targetException?.message}")
                    } catch (e: Exception) {
                        testsFailed++
                        failures.add("$className.${method.name}: ${e.message}")
                    } finally {
                        for (teardown in afterEachMethods) {
                            try {
                                teardown.isAccessible = true
                                teardown.invoke(instance)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            }
        }

        // Handle @Nested inner classes
        val nestedClasses =
            testClass.declaredClasses.filter { innerClass ->
                innerClass.isAnnotationPresent(Nested::class.java)
            }

        for (nestedClass in nestedClasses) {
            val nestedResults = runTestClass(nestedClass, "${className}\$${nestedClass.simpleName}")
            testsFound += nestedResults.first
            testsSucceeded += nestedResults.second
            testsFailed += nestedResults.third
            failures.addAll(nestedResults.fourth)
        }

        return TestClassResult(testsFound, testsSucceeded, testsFailed, failures)
    }

    /**
     * Runs a @ParameterizedTest method.
     * Tries to invoke the method multiple times, simulating parameterized behavior.
     */
    private fun runParameterizedTest(
        testClass: Class<*>,
        method: java.lang.reflect.Method,
        beforeEachMethods: List<java.lang.reflect.Method>,
        afterEachMethods: List<java.lang.reflect.Method>,
    ): TestClassResult {
        var testsFound = 0
        var testsSucceeded = 0
        var testsFailed = 0
        val failures = mutableListOf<String>()

        // For @ParameterizedTest, try to find @MethodSource or @ValueSource
        val methodSource = method.getAnnotation(MethodSource::class.java)
        val valueSource = method.getAnnotation(ValueSource::class.java)

        val paramCount = method.parameterTypes.size

        if (paramCount == 0) {
            // No parameters — treat as regular test
            testsFound++
            val instance = testClass.getDeclaredConstructor().newInstance()
            try {
                for (setup in beforeEachMethods) {
                    setup.isAccessible = true
                    setup.invoke(instance)
                }
                method.invoke(instance)
                testsSucceeded++
            } catch (e: java.lang.reflect.InvocationTargetException) {
                testsFailed++
                failures.add("${testClass.name}.${method.name}: ${e.targetException?.message}")
            } catch (e: Exception) {
                testsFailed++
                failures.add("${testClass.name}.${method.name}: ${e.message}")
            } finally {
                for (teardown in afterEachMethods) {
                    try {
                        teardown.isAccessible = true
                        teardown.invoke(instance)
                    } catch (_: Exception) {
                    }
                }
            }
            return TestClassResult(testsFound, testsSucceeded, testsFailed, failures)
        }

        // Try to get parameters from @MethodSource
        if (methodSource != null) {
            val factoryMethodName = methodSource.value.firstOrNull()
            if (factoryMethodName != null) {
                try {
                    // Look for factory method in same class or companion object
                    val factoryMethod = findFactoryMethod(testClass, factoryMethodName)
                    if (factoryMethod != null && factoryMethod.parameterTypes.isEmpty()) {
                        val params = factoryMethod.invoke(null)

                        if (params is Iterable<*>) {
                            for (param in params) {
                                testsFound++
                                val instance = testClass.getDeclaredConstructor().newInstance()
                                try {
                                    for (setup in beforeEachMethods) {
                                        setup.isAccessible = true
                                        setup.invoke(instance)
                                    }
                                    when (param) {
                                        is Array<*> -> method.invoke(instance, *param)
                                        else -> method.invoke(instance, param)
                                    }
                                    testsSucceeded++
                                } catch (e: java.lang.reflect.InvocationTargetException) {
                                    testsFailed++
                                    failures.add("${testClass.name}.${method.name}: ${e.targetException?.message}")
                                } catch (e: Exception) {
                                    testsFailed++
                                    failures.add("${testClass.name}.${method.name}: ${e.message}")
                                } finally {
                                    for (teardown in afterEachMethods) {
                                        try {
                                            teardown.isAccessible = true
                                            teardown.invoke(instance)
                                        } catch (_: Exception) {
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.debug("Could not invoke parameterized test factory: $factoryMethodName", e)
                }
            }
        }

        // Fallback: try @ValueSource
        if (valueSource != null && testsFound == 0) {
            val strings = valueSource.strings
            val ints = valueSource.ints
            val longs = valueSource.longs
            val floats = valueSource.floats
            val doubles = valueSource.doubles

            val values: List<Any?> =
                when {
                    strings.isNotEmpty() -> strings.toList()
                    ints.isNotEmpty() -> ints.toList()
                    longs.isNotEmpty() -> longs.toList()
                    floats.isNotEmpty() -> floats.toList()
                    doubles.isNotEmpty() -> doubles.toList()
                    else -> emptyList()
                }

            for (value in values) {
                testsFound++
                val instance = testClass.getDeclaredConstructor().newInstance()
                try {
                    for (setup in beforeEachMethods) {
                        setup.isAccessible = true
                        setup.invoke(instance)
                    }
                    method.invoke(instance, value)
                    testsSucceeded++
                } catch (e: java.lang.reflect.InvocationTargetException) {
                    testsFailed++
                    failures.add("${testClass.name}.${method.name}: ${e.targetException?.message}")
                } catch (e: Exception) {
                    testsFailed++
                    failures.add("${testClass.name}.${method.name}: ${e.message}")
                } finally {
                    for (teardown in afterEachMethods) {
                        try {
                            teardown.isAccessible = true
                            teardown.invoke(instance)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }

        // If we couldn't resolve parameters, at least count the test as found
        if (testsFound == 0) {
            testsFound = 1
            failures.add("${testClass.name}.${method.name}: Could not resolve @ParameterizedTest parameters")
        }

        return TestClassResult(testsFound, testsSucceeded, testsFailed, failures)
    }

    /**
     * Finds a factory method for @MethodSource.
     * Checks same class, companion object, and top-level functions.
     */
    private fun findFactoryMethod(
        testClass: Class<*>,
        methodName: String,
    ): java.lang.reflect.Method? {
        // Try same class
        try {
            return testClass.getDeclaredMethod(methodName)
        } catch (_: NoSuchMethodException) {
        }

        // Try companion object (Kotlin)
        try {
            val companionClass =
                testClass.getDeclaredClasses().find {
                    it.name.endsWith("\$Companion")
                }
            if (companionClass != null) {
                val companion = testClass.getDeclaredField("Companion").get(null)
                return companionClass.getDeclaredMethod(methodName).apply { isAccessible = true }
            }
        } catch (_: Exception) {
        }

        // Try static methods
        try {
            return testClass.getMethod(methodName)
        } catch (_: NoSuchMethodException) {
        }

        return null
    }

    private data class TestClassResult(
        val first: Int,
        val second: Int,
        val third: Int,
        val fourth: List<String>,
    )
}
