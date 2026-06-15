package com.github.rodrigotimoteo.mutation.runner

import org.junit.jupiter.api.Disabled
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
                val cause = if (e is java.lang.reflect.InvocationTargetException) e.targetException?.message else e.message
                val errorMsg = "Could not load test class $testClassName: $cause"
                logger.warn(errorMsg)
                failures.add(errorMsg)
                testsFailed++
                failedTestClasses.add(testClassName)
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
        var testsSkipped = 0
        val failures = mutableListOf<String>()

        // Resolve the JUnit annotation classes from the *test* classloader
        // rather than the runner's classloader. The engine classloader
        // (mutation-core) may have a different copy of JUnit than the test
        // runtime, in which case `isAnnotationPresent` returns false even
        // though the method carries `@Test` from the test classloader's
        // copy. Resolve once per test class and reuse below.
        val junitTestClass = tryGetClass(testClass, "org.junit.Test")
        val junit5TestClass = tryGetClass(testClass, "org.junit.jupiter.api.Test")
        val parameterizedTestClass =
            tryGetClass(testClass, "org.junit.jupiter.params.ParameterizedTest")
        val repeatedTestClass = tryGetClass(testClass, "org.junit.jupiter.api.RepeatedTest")
        val disabledClass = tryGetClass(testClass, "org.junit.jupiter.api.Disabled")
        val beforeEachClass = tryGetClass(testClass, "org.junit.jupiter.api.BeforeEach")
        val afterEachClass = tryGetClass(testClass, "org.junit.jupiter.api.AfterEach")
        val beforeClass = tryGetClass(testClass, "org.junit.Before")
        val afterClass = tryGetClass(testClass, "org.junit.After")
        val beforeAllClass = tryGetClass(testClass, "org.junit.jupiter.api.BeforeAll")
        val afterAllClass = tryGetClass(testClass, "org.junit.jupiter.api.AfterAll")
        val beforeAllJunit4Class = tryGetClass(testClass, "org.junit.BeforeClass")
        val afterAllJunit4Class = tryGetClass(testClass, "org.junit.AfterClass")
        val nestedClass = tryGetClass(testClass, "org.junit.jupiter.api.Nested")

        fun java.lang.reflect.Method.isTestAnnotation(): Boolean {
            if (junitTestClass != null && isAnnotationPresent(junitTestClass)) return true
            if (junit5TestClass != null && isAnnotationPresent(junit5TestClass)) return true
            if (parameterizedTestClass != null && isAnnotationPresent(parameterizedTestClass)) return true
            if (repeatedTestClass != null && isAnnotationPresent(repeatedTestClass)) return true
            return false
        }

        // Skip disabled classes
        if (testClass.isAnnotationPresent(Disabled::class.java) ||
            (disabledClass != null && testClass.isAnnotationPresent(disabledClass))
        ) {
            return TestClassResult(0, 0, 0, 0, failures)
        }

        // Count @Disabled test methods (excluded by filter below) as skipped
        val allDeclaredMethods = testClass.allDeclaredMethods()
        val candidateMethods =
            allDeclaredMethods.filter { method -> method.isTestAnnotation() }
        candidateMethods.forEach { method ->
            if (disabledClass != null && method.isAnnotationPresent(disabledClass)) {
                testsSkipped +=
                    if (repeatedTestClass != null) {
                        try {
                            val ann = method.getAnnotation(repeatedTestClass)
                            (repeatedTestClass.getMethod("value").invoke(ann) as? Int) ?: 1
                        } catch (_: Exception) {
                            1
                        }
                    } else {
                        1
                    }
            }
        }

        // Discover test methods (@Test, @ParameterizedTest, @RepeatedTest) — walk superclass hierarchy
        // Support both JUnit 5 (org.junit.jupiter.api.Test) and JUnit 4 (org.junit.Test)
        val testMethods =
            allDeclaredMethods.filter { method ->
                (disabledClass == null || !method.isAnnotationPresent(disabledClass)) && method.isTestAnnotation()
            }

        // Discover lifecycle methods — walk superclass hierarchy
        // Support both JUnit 5 (@BeforeEach/@AfterEach) and JUnit 4 (@Before/@After)
        val beforeEachMethods =
            testClass.allDeclaredMethods().filter {
                (beforeEachClass != null && it.isAnnotationPresent(beforeEachClass)) ||
                    (beforeClass != null && it.isAnnotationPresent(beforeClass))
            }
        val afterEachMethods =
            testClass.allDeclaredMethods().filter {
                (afterEachClass != null && it.isAnnotationPresent(afterEachClass)) ||
                    (afterClass != null && it.isAnnotationPresent(afterClass))
            }

        // Discover @BeforeAll/@AfterAll (static or instance methods)
        // Support both JUnit 5 (@BeforeAll/@AfterAll) and JUnit 4 (@BeforeClass/@AfterClass)
        val beforeAllMethods =
            testClass.allDeclaredMethods().filter {
                (beforeAllClass != null && it.isAnnotationPresent(beforeAllClass)) ||
                    (beforeAllJunit4Class != null && it.isAnnotationPresent(beforeAllJunit4Class))
            }
        val afterAllMethods =
            testClass.allDeclaredMethods().filter {
                (afterAllClass != null && it.isAnnotationPresent(afterAllClass)) ||
                    (afterAllJunit4Class != null && it.isAnnotationPresent(afterAllJunit4Class))
            }

        // Run @BeforeAll once before all tests — abort on failure
        var beforeAllFailed = false
        for (setup in beforeAllMethods) {
            try {
                setup.isAccessible = true
                if (java.lang.reflect.Modifier.isStatic(setup.modifiers)) {
                    setup.invoke(null)
                } else {
                    val instance = createInstance(testClass, null)
                    setup.invoke(instance)
                }
            } catch (e: Exception) {
                val cause = if (e is java.lang.reflect.InvocationTargetException) e.targetException?.message else e.message
                failures.add("$className.@BeforeAll ${setup.name}: $cause")
                beforeAllFailed = true
            }
        }

        if (beforeAllFailed) {
            return TestClassResult(0, 0, 1, 0, failures)
        }

        // Run each test method
        for (method in testMethods) {
            if (parameterizedTestClass != null && method.isAnnotationPresent(parameterizedTestClass)) {
                // Handle @ParameterizedTest — invoke multiple times with different arguments
                val paramResults = runParameterizedTest(testClass, method, beforeEachMethods, afterEachMethods)
                testsFound += paramResults.testsFound
                testsSucceeded += paramResults.testsSucceeded
                testsFailed += paramResults.testsFailed
                failures.addAll(paramResults.failures)
            } else {
                // Standard @Test or @RepeatedTest
                val repetitions =
                    if (repeatedTestClass != null) {
                        val repAnnotation = method.getAnnotation(repeatedTestClass)
                        try {
                            @Suppress("UNCHECKED_CAST")
                            (repeatedTestClass.getMethod("value").invoke(repAnnotation) as? Int) ?: 1
                        } catch (_: Exception) {
                            1
                        }
                    } else {
                        1
                    }
                testsFound += repetitions
                for (rep in 1..repetitions) {
                    val instance = createInstance(testClass, null)
                    val result =
                        invokeWithLifecycle(
                            instance = instance,
                            method = method,
                            beforeEach = beforeEachMethods,
                            afterEach = afterEachMethods,
                            className = className,
                        )
                    when (result) {
                        is TestInvocationResult.Success -> testsSucceeded++
                        is TestInvocationResult.Failure -> {
                            testsFailed++
                            failures.add(result.message)
                        }
                    }
                }
            }
        }

        // Handle @Nested inner classes — need outer instance for constructor
        val nestedClasses =
            if (nestedClass != null) {
                testClass.declaredClasses.filter { innerClass -> innerClass.isAnnotationPresent(nestedClass) }
            } else {
                emptyList()
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

        // Run @AfterAll once after all tests
        for (teardown in afterAllMethods) {
            try {
                teardown.isAccessible = true
                if (java.lang.reflect.Modifier.isStatic(teardown.modifiers)) {
                    teardown.invoke(null)
                } else {
                    val instance = createInstance(testClass, null)
                    teardown.invoke(instance)
                }
            } catch (e: Exception) {
                val cause = if (e is java.lang.reflect.InvocationTargetException) e.targetException?.message else e.message
                failures.add("$className.@AfterAll ${teardown.name}: $cause")
            }
        }

        return TestClassResult(testsFound, testsSucceeded, testsFailed, testsSkipped, failures)
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
        var testsSkipped = 0
        val failures = mutableListOf<String>()

        val methodSource = method.getAnnotation(MethodSource::class.java)
        val valueSource = method.getAnnotation(ValueSource::class.java)

        val paramCount = method.parameterTypes.size

        if (paramCount == 0) {
            testsFound++
            val instance = createInstance(testClass, outerInstance)
            val result =
                invokeWithLifecycle(
                    instance = instance,
                    method = method,
                    beforeEach = beforeEachMethods,
                    afterEach = afterEachMethods,
                    className = testClass.name,
                )
            when (result) {
                is TestInvocationResult.Success -> testsSucceeded++
                is TestInvocationResult.Failure -> {
                    testsFailed++
                    failures.add(result.message)
                }
            }
        } else {
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
                                    val inst = createInstance(testClass, outerInstance)
                                    val args: Array<Any?> =
                                        when (param) {
                                            is Array<*> -> Array(param.size) { param[it] }
                                            else -> arrayOf(param)
                                        }
                                    val result =
                                        invokeWithLifecycle(
                                            instance = inst,
                                            method = method,
                                            args = args,
                                            beforeEach = beforeEachMethods,
                                            afterEach = afterEachMethods,
                                            className = testClass.name,
                                        )
                                    when (result) {
                                        is TestInvocationResult.Success -> testsSucceeded++
                                        is TestInvocationResult.Failure -> {
                                            testsFailed++
                                            failures.add(result.message)
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
                val values: List<Any?> =
                    when {
                        valueSource.strings.isNotEmpty() -> valueSource.strings.toList()
                        valueSource.ints.isNotEmpty() -> valueSource.ints.toList()
                        valueSource.longs.isNotEmpty() -> valueSource.longs.toList()
                        valueSource.floats.isNotEmpty() -> valueSource.floats.toList()
                        valueSource.doubles.isNotEmpty() -> valueSource.doubles.toList()
                        else -> emptyList()
                    }

                for (value in values) {
                    testsFound++
                    val inst = createInstance(testClass, outerInstance)
                    val result =
                        invokeWithLifecycle(
                            instance = inst,
                            method = method,
                            args = arrayOf(value),
                            beforeEach = beforeEachMethods,
                            afterEach = afterEachMethods,
                            className = testClass.name,
                        )
                    when (result) {
                        is TestInvocationResult.Success -> testsSucceeded++
                        is TestInvocationResult.Failure -> {
                            testsFailed++
                            failures.add(result.message)
                        }
                    }
                }
            }
        }

        // If we couldn't resolve parameters, don't inflate test count (NO_COVERAGE is more accurate)
        if (testsFound == 0) {
            failures.add("${testClass.name}.${method.name}: Could not resolve @ParameterizedTest parameters")
        }

        return TestClassResult(testsFound, testsSucceeded, testsFailed, testsSkipped, failures)
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
        var testsSkipped = 0
        val failures = mutableListOf<String>()

        if (testClass.isAnnotationPresent(Disabled::class.java)) {
            return TestClassResult(0, 0, 0, 0, failures)
        }

        val allDeclaredMethods = testClass.allDeclaredMethods()
        val junit5TestClass = tryGetClass(testClass, "org.junit.jupiter.api.Test")
        val parameterizedTestClass = tryGetClass(testClass, "org.junit.jupiter.params.ParameterizedTest")
        val repeatedTestClass = tryGetClass(testClass, "org.junit.jupiter.api.RepeatedTest")
        val disabledClass = tryGetClass(testClass, "org.junit.jupiter.api.Disabled")
        val beforeEachClass = tryGetClass(testClass, "org.junit.jupiter.api.BeforeEach")
        val afterEachClass = tryGetClass(testClass, "org.junit.jupiter.api.AfterEach")
        val nestedClass = tryGetClass(testClass, "org.junit.jupiter.api.Nested")

        fun java.lang.reflect.Method.isNestedTestAnnotation(): Boolean {
            if (junit5TestClass != null && isAnnotationPresent(junit5TestClass)) return true
            if (repeatedTestClass != null && isAnnotationPresent(repeatedTestClass)) return true
            if (parameterizedTestClass != null && isAnnotationPresent(parameterizedTestClass)) return true
            return false
        }

        val candidateMethods =
            allDeclaredMethods.filter { method -> method.isNestedTestAnnotation() }
        candidateMethods.forEach { method ->
            if (disabledClass != null && method.isAnnotationPresent(disabledClass)) {
                testsSkipped +=
                    if (repeatedTestClass != null) {
                        try {
                            val ann = method.getAnnotation(repeatedTestClass)
                            (repeatedTestClass.getMethod("value").invoke(ann) as? Int) ?: 1
                        } catch (_: Exception) {
                            1
                        }
                    } else {
                        1
                    }
            }
        }

        val testMethods =
            allDeclaredMethods.filter { method ->
                (disabledClass == null || !method.isAnnotationPresent(disabledClass)) && method.isNestedTestAnnotation()
            }

        // JUnit 5: @Nested runs parent lifecycle methods too
        val ownBeforeEach =
            testClass.allDeclaredMethods().filter {
                beforeEachClass != null && it.isAnnotationPresent(beforeEachClass)
            }
        val ownAfterEach =
            testClass.allDeclaredMethods().filter {
                afterEachClass != null && it.isAnnotationPresent(afterEachClass)
            }
        val beforeEachMethods = parentBeforeEach + ownBeforeEach
        val afterEachMethods = ownAfterEach + parentAfterEach

        for (method in testMethods) {
            if (parameterizedTestClass != null && method.isAnnotationPresent(parameterizedTestClass)) {
                val paramResults = runParameterizedTest(testClass, method, beforeEachMethods, afterEachMethods, outerInstance)
                testsFound += paramResults.testsFound
                testsSucceeded += paramResults.testsSucceeded
                testsFailed += paramResults.testsFailed
                failures.addAll(paramResults.failures)
            } else {
                val repetitions =
                    if (repeatedTestClass != null) {
                        val ann = method.getAnnotation(repeatedTestClass)
                        try {
                            (repeatedTestClass.getMethod("value").invoke(ann) as? Int) ?: 1
                        } catch (_: Exception) {
                            1
                        }
                    } else {
                        1
                    }
                testsFound += repetitions
                for (rep in 1..repetitions) {
                    val instance = createInstance(testClass, outerInstance)
                    val result =
                        invokeWithLifecycle(
                            instance = instance,
                            method = method,
                            beforeEach = beforeEachMethods,
                            afterEach = afterEachMethods,
                            className = className,
                        )
                    when (result) {
                        is TestInvocationResult.Success -> testsSucceeded++
                        is TestInvocationResult.Failure -> {
                            testsFailed++
                            failures.add(result.message)
                        }
                    }
                }
            }
        }

        // Recurse into nested classes — pass current instance as outer for deeper nesting
        val nestedClasses =
            if (nestedClass != null) {
                testClass.declaredClasses.filter { it.isAnnotationPresent(nestedClass) }
            } else {
                emptyList()
            }
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
                        parentAfterEach = afterEachMethods,
                    )
                testsFound += nestedResults.testsFound
                testsSucceeded += nestedResults.testsSucceeded
                testsFailed += nestedResults.testsFailed
                testsSkipped += nestedResults.testsSkipped
                failures.addAll(nestedResults.failures)
            }
        }

        return TestClassResult(testsFound, testsSucceeded, testsFailed, testsSkipped, failures)
    }

    /**
     * Invokes a test method with @BeforeEach / @AfterEach lifecycle, returning a
     * structured result. Centralises the duplicated try/catch/finally blocks that
     * previously appeared in every standard test, @ParameterizedTest, and @Nested
     * call site.
     */
    private fun invokeWithLifecycle(
        instance: Any?,
        method: java.lang.reflect.Method,
        args: Array<Any?> = emptyArray(),
        beforeEach: List<java.lang.reflect.Method>,
        afterEach: List<java.lang.reflect.Method>,
        className: String,
    ): TestInvocationResult {
        try {
            for (setup in beforeEach) {
                setup.isAccessible = true
                setup.invoke(instance)
            }
            method.invoke(instance, *args)
            return TestInvocationResult.Success
        } catch (e: java.lang.reflect.InvocationTargetException) {
            return TestInvocationResult.Failure("$className.${method.name}: ${e.targetException?.message}")
        } catch (e: Exception) {
            return TestInvocationResult.Failure("$className.${method.name}: ${e.message}")
        } finally {
            for (teardown in afterEach) {
                try {
                    teardown.isAccessible = true
                    teardown.invoke(instance)
                } catch (_: Exception) {
                }
            }
        }
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

    /**
     * Resolve [name] via the [anchor] class's loader. Used to read JUnit
     * annotations loaded by the *test* runtime classloader (not the
     * engine's classloader) so `isAnnotationPresent` returns true when the
     * test method carries `@org.junit.Test` from the user's JUnit 4 jar.
     */
    @Suppress("UNCHECKED_CAST")
    private fun tryGetClass(
        anchor: Class<*>,
        name: String,
    ): Class<out Annotation>? =
        try {
            Class.forName(name, false, anchor.classLoader) as? Class<out Annotation>
        } catch (_: ClassNotFoundException) {
            null
        }

    private data class TestClassResult(
        val testsFound: Int,
        val testsSucceeded: Int,
        val testsFailed: Int,
        val testsSkipped: Int,
        val failures: List<String>,
    )

    sealed class TestInvocationResult {
        data object Success : TestInvocationResult()

        data class Failure(val message: String) : TestInvocationResult()
    }
}
