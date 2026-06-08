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
                testsFound += results.testsFound
                testsSucceeded += results.testsSucceeded
                testsFailed += results.testsFailed
                failures.addAll(results.failures)
                if (results.testsFailed > 0) {
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

        // Discover test methods (@Test, @ParameterizedTest, @RepeatedTest) — walk superclass hierarchy
        val testMethods =
            testClass.allDeclaredMethods().filter { method ->
                method.isAnnotationPresent(Test::class.java) ||
                    method.isAnnotationPresent(ParameterizedTest::class.java) ||
                    method.isAnnotationPresent(RepeatedTest::class.java)
            }

        // Discover lifecycle methods — walk superclass hierarchy
        val beforeEachMethods =
            testClass.allDeclaredMethods().filter {
                it.isAnnotationPresent(BeforeEach::class.java)
            }
        val afterEachMethods =
            testClass.allDeclaredMethods().filter {
                it.isAnnotationPresent(AfterEach::class.java)
            }

        // Run each test method
        for (method in testMethods) {
            if (method.isAnnotationPresent(ParameterizedTest::class.java)) {
                // Handle @ParameterizedTest — invoke multiple times with different arguments
                val paramResults = runParameterizedTest(testClass, method, beforeEachMethods, afterEachMethods)
                testsFound += paramResults.testsFound
                testsSucceeded += paramResults.testsSucceeded
                testsFailed += paramResults.testsFailed
                failures.addAll(paramResults.failures)
            } else {
                // Standard @Test or @RepeatedTest
                val repeatedAnnotation = method.getAnnotation(RepeatedTest::class.java)
                val repetitions = repeatedAnnotation?.value ?: 1
                testsFound += repetitions
                for (rep in 1..repetitions) {
                    val instance = createInstance(testClass, null)
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

        // Handle @Nested inner classes — need outer instance for constructor
        val nestedClasses =
            testClass.declaredClasses.filter { innerClass ->
                innerClass.isAnnotationPresent(Nested::class.java)
            }

        if (nestedClasses.isNotEmpty()) {
            val outerInstance = createInstance(testClass, null)

            for (nestedClass in nestedClasses) {
                val nestedResults =
                    runNestedTestClass(
                        nestedClass,
                        outerInstance,
                        "${className}\$${nestedClass.simpleName}",
                        parentBeforeEach = beforeEachMethods,
                        parentAfterEach = afterEachMethods,
                    )
                testsFound += nestedResults.testsFound
                testsSucceeded += nestedResults.testsSucceeded
                testsFailed += nestedResults.testsFailed
                failures.addAll(nestedResults.failures)
            }
        }

        return TestClassResult(testsFound, testsSucceeded, testsFailed, failures)
    }

    /**
     * Runs a @ParameterizedTest method with outer instance support for @Nested.
     */
    private fun runParameterizedTest(
        testClass: Class<*>,
        method: java.lang.reflect.Method,
        beforeEachMethods: List<java.lang.reflect.Method>,
        afterEachMethods: List<java.lang.reflect.Method>,
        outerInstance: Any? = null,
    ): TestClassResult {
        var testsFound = 0
        var testsSucceeded = 0
        var testsFailed = 0
        val failures = mutableListOf<String>()

        val methodSource = method.getAnnotation(MethodSource::class.java)
        val valueSource = method.getAnnotation(ValueSource::class.java)

        val paramCount = method.parameterTypes.size

        if (paramCount == 0) {
            testsFound++
            val instance = createInstance(testClass, outerInstance)
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
                    val (factoryMethod, receiver) = findFactoryMethod(testClass, factoryMethodName) ?: (null to null)
                    if (factoryMethod != null && factoryMethod.parameterTypes.isEmpty()) {
                        val params = factoryMethod.invoke(receiver)

                        if (params is Iterable<*>) {
                            for (param in params) {
                                testsFound++
                                val instance = createInstance(testClass, outerInstance)
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
                val instance = createInstance(testClass, outerInstance)
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

        // If we couldn't resolve parameters, don't inflate test count (NO_COVERAGE is more accurate)
        if (testsFound == 0) {
            failures.add("${testClass.name}.${method.name}: Could not resolve @ParameterizedTest parameters")
        }

        return TestClassResult(testsFound, testsSucceeded, testsFailed, failures)
    }

    /**
     * Runs a @Nested inner class with the outer instance for proper constructor injection.
     */
    private fun runNestedTestClass(
        testClass: Class<*>,
        outerInstance: Any?,
        className: String,
        parentBeforeEach: List<java.lang.reflect.Method> = emptyList(),
        parentAfterEach: List<java.lang.reflect.Method> = emptyList(),
    ): TestClassResult {
        var testsFound = 0
        var testsSucceeded = 0
        var testsFailed = 0
        val failures = mutableListOf<String>()

        val testMethods =
            testClass.allDeclaredMethods().filter { method ->
                method.isAnnotationPresent(org.junit.jupiter.api.Test::class.java) ||
                    method.isAnnotationPresent(RepeatedTest::class.java) ||
                    method.isAnnotationPresent(ParameterizedTest::class.java)
            }

        // JUnit 5: @Nested runs parent lifecycle methods too
        val ownBeforeEach = testClass.allDeclaredMethods().filter { it.isAnnotationPresent(BeforeEach::class.java) }
        val ownAfterEach = testClass.allDeclaredMethods().filter { it.isAnnotationPresent(AfterEach::class.java) }
        val beforeEachMethods = parentBeforeEach + ownBeforeEach
        val afterEachMethods = ownAfterEach + parentAfterEach

        for (method in testMethods) {
            if (method.isAnnotationPresent(ParameterizedTest::class.java)) {
                val paramResults = runParameterizedTest(testClass, method, beforeEachMethods, afterEachMethods, outerInstance)
                testsFound += paramResults.testsFound
                testsSucceeded += paramResults.testsSucceeded
                testsFailed += paramResults.testsFailed
                failures.addAll(paramResults.failures)
            } else {
                val repeatedAnnotation = method.getAnnotation(RepeatedTest::class.java)
                val repetitions = repeatedAnnotation?.value ?: 1
                testsFound += repetitions
                for (rep in 1..repetitions) {
                    val instance = createInstance(testClass, outerInstance)
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

        // Recurse into nested classes — pass current instance as outer for deeper nesting
        val nestedClasses = testClass.declaredClasses.filter { it.isAnnotationPresent(Nested::class.java) }
        if (nestedClasses.isNotEmpty()) {
            // Create an instance of this nested class to serve as outer for deeper nesting
            val currentInstance = createInstance(testClass, outerInstance)

            for (nestedClass in nestedClasses) {
                val nestedResults =
                    runNestedTestClass(
                        nestedClass,
                        currentInstance,
                        "$className\$${nestedClass.simpleName}",
                        parentBeforeEach = beforeEachMethods,
                        parentAfterEach = emptyList(),
                    )
                testsFound += nestedResults.testsFound
                testsSucceeded += nestedResults.testsSucceeded
                testsFailed += nestedResults.testsFailed
                failures.addAll(nestedResults.failures)
            }
        }

        return TestClassResult(testsFound, testsSucceeded, testsFailed, failures)
    }

    /**
     * Creates an instance of a test class, handling @Nested constructor requirements.
     */
    private fun createInstance(
        testClass: Class<*>,
        outerInstance: Any?,
    ): Any {
        return if (outerInstance != null) {
            try {
                testClass.getDeclaredConstructor(testClass.declaringClass).newInstance(outerInstance)
            } catch (_: Exception) {
                try {
                    testClass.getDeclaredConstructor().newInstance()
                } catch (_: Exception) {
                    throw IllegalStateException(
                        "Cannot instantiate ${testClass.name}: no-arg constructor not found and no enclosing instance available",
                    )
                }
            }
        } else {
            testClass.getDeclaredConstructor().newInstance()
        }
    }

    /**
     * Finds a factory method for @MethodSource.
     * Returns the method and an optional receiver (for companion object methods).
     */
    private fun findFactoryMethod(
        testClass: Class<*>,
        methodName: String,
    ): Pair<java.lang.reflect.Method, Any?>? {
        // Try same class (static method)
        try {
            return testClass.getDeclaredMethod(methodName) to null
        } catch (_: NoSuchMethodException) {
        }

        // Try companion object (Kotlin)
        try {
            val companionClass =
                testClass.declaredClasses.find {
                    it.name.endsWith("\$Companion")
                }
            if (companionClass != null) {
                val companionField = testClass.getDeclaredField("Companion")
                companionField.isAccessible = true
                val companion = companionField.get(null)
                val method = companionClass.getDeclaredMethod(methodName)
                method.isAccessible = true
                return method to companion
            }
        } catch (_: Exception) {
        }

        // Try static methods
        try {
            return testClass.getMethod(methodName) to null
        } catch (_: NoSuchMethodException) {
        }

        return null
    }

    /**
     * Walk superclass hierarchy to find all declared methods.
     */
    private fun Class<*>.allDeclaredMethods(): List<java.lang.reflect.Method> {
        val methods = mutableListOf<java.lang.reflect.Method>()
        var cls: Class<*> = this
        while (cls != null && cls != Any::class.java) {
            methods.addAll(cls.declaredMethods.toList())
            cls = cls.superclass
        }
        return methods
    }

    private data class TestClassResult(
        val testsFound: Int,
        val testsSucceeded: Int,
        val testsFailed: Int,
        val failures: List<String>,
    )
}
