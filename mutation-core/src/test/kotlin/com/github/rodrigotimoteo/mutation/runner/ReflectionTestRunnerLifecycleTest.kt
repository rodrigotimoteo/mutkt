package com.github.rodrigotimoteo.mutation.runner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReflectionTestRunnerLifecycleTest {
    private val runner = ReflectionTestRunner(ClassLoader.getSystemClassLoader())

    @org.junit.jupiter.api.BeforeEach
    fun resetCounters() {
        LifecycleTest.beforeAllCount = 0
        LifecycleTest.afterAllCount = 0
        LifecycleTest.beforeEachCount = 0
        LifecycleTest.afterEachCount = 0
    }

    @Test
    fun `BeforeAll runs once before all tests`() {
        val results = runner.runTests(listOf(LifecycleTest::class.java.name))

        assertEquals(2, results.testsFound)
        assertEquals(2, results.testsSucceeded)
        assertEquals(0, results.testsFailed)
        assertEquals(1, LifecycleTest.beforeAllCount)
    }

    @Test
    fun `AfterAll runs once after all tests`() {
        val results = runner.runTests(listOf(LifecycleTest::class.java.name))

        assertEquals(2, results.testsFound)
        assertEquals(1, LifecycleTest.afterAllCount)
    }

    @Test
    fun `BeforeEach runs before each test`() {
        val results = runner.runTests(listOf(LifecycleTest::class.java.name))

        assertEquals(2, results.testsFound)
        assertEquals(2, LifecycleTest.beforeEachCount)
    }

    @Test
    fun `AfterEach runs after each test`() {
        val results = runner.runTests(listOf(LifecycleTest::class.java.name))

        assertEquals(2, results.testsFound)
        assertEquals(2, LifecycleTest.afterEachCount)
    }

    @Test
    fun `BeforeAll failure aborts all tests`() {
        val results = runner.runTests(listOf(BeforeAllFailTest::class.java.name))

        assertEquals(0, results.testsFound)
        assertEquals(1, results.testsFailed)
        assertTrue(results.failureMessages.any { it.contains("@BeforeAll") })
    }

    @Test
    fun `lifecycle order is BeforeAll, BeforeEach, Test, AfterEach, AfterAll`() {
        LifecycleOrderTest.executionOrder.clear()
        runner.runTests(listOf(LifecycleOrderTest::class.java.name))

        val order = LifecycleOrderTest.executionOrder
        assertTrue(order.size >= 7, "Expected at least 7 lifecycle events, got ${order.size}: $order")
        assertTrue(order[0] == "BeforeAll", "First event should be BeforeAll")
        assertTrue(order.last() == "AfterAll", "Last event should be AfterAll")
    }
}

/**
 * Test class with all lifecycle methods.
 * Static counters to track invocation counts.
 */
class LifecycleTest {
    companion object {
        var beforeAllCount = 0
        var afterAllCount = 0
        var beforeEachCount = 0
        var afterEachCount = 0

        @JvmStatic
        @org.junit.jupiter.api.BeforeAll
        fun setupAll() {
            beforeAllCount++
        }

        @JvmStatic
        @org.junit.jupiter.api.AfterAll
        fun teardownAll() {
            afterAllCount++
        }
    }

    @org.junit.jupiter.api.BeforeEach
    fun setup() {
        beforeEachCount++
    }

    @org.junit.jupiter.api.AfterEach
    fun teardown() {
        afterEachCount++
    }

    @org.junit.jupiter.api.Test
    fun test1() {
        assertEquals(1, 1)
    }

    @org.junit.jupiter.api.Test
    fun test2() {
        assertEquals(1, 1)
    }
}

/**
 * Test class where @BeforeAll fails.
 * Disabled for JUnit — only run via ReflectionTestRunner.
 */
@org.junit.jupiter.api.Disabled("Only run via ReflectionTestRunner, not JUnit directly")
class BeforeAllFailTest {
    companion object {
        @JvmStatic
        @org.junit.jupiter.api.BeforeAll
        fun failingSetup() {
            throw RuntimeException("Setup failed intentionally")
        }
    }

    @org.junit.jupiter.api.Test
    fun shouldNotRun() {
        assertEquals(1, 1)
    }
}

/**
 * Test class to verify lifecycle execution order.
 */
class LifecycleOrderTest {
    companion object {
        val executionOrder = mutableListOf<String>()

        @JvmStatic
        @org.junit.jupiter.api.BeforeAll
        fun setupAll() {
            executionOrder.add("BeforeAll")
        }

        @JvmStatic
        @org.junit.jupiter.api.AfterAll
        fun teardownAll() {
            executionOrder.add("AfterAll")
        }
    }

    @org.junit.jupiter.api.BeforeEach
    fun setup() {
        executionOrder.add("BeforeEach")
    }

    @org.junit.jupiter.api.AfterEach
    fun teardown() {
        executionOrder.add("AfterEach")
    }

    @org.junit.jupiter.api.Test
    fun test1() {
        executionOrder.add("Test1")
    }

    @org.junit.jupiter.api.Test
    fun test2() {
        executionOrder.add("Test2")
    }
}
