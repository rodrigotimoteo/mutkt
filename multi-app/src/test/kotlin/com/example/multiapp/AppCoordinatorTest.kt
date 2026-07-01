package com.example.multiapp

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppCoordinatorTest {

    private lateinit var coordinator: AppCoordinator

    @Before
    fun setUp() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
        coordinator = AppCoordinator()
    }

    @Test
    fun `formatCount returns negative placeholder for negative values`() {
        assertEquals("<negative>", coordinator.formatCount(-1))
        assertEquals("<negative>", coordinator.formatCount(-100))
    }

    @Test
    fun `formatCount returns zero for zero`() {
        assertEquals("0", coordinator.formatCount(0))
    }

    @Test
    fun `formatCount returns formatted value for positive values`() {
        assertEquals("value=1", coordinator.formatCount(1))
        assertEquals("value=42", coordinator.formatCount(42))
    }

    @Test
    fun `summarize combines input and count`() {
        assertEquals("hello:3", coordinator.summarize("hello", 3))
        assertEquals("hello:0", coordinator.summarize("hello", 0))
    }

    @Test
    fun `summarize replaces blank input with placeholder`() {
        assertEquals("<empty>:1", coordinator.summarize("", 1))
        assertEquals("<empty>:2", coordinator.summarize("   ", 2))
    }

    @Test
    fun `summarize clamps negative count to zero`() {
        assertEquals("hi:0", coordinator.summarize("hi", -5))
    }

    @Test
    fun `bridgeDescribe delegates to SharedLogic for each branch`() {
        assertEquals("negative", coordinator.bridgeDescribe(-1))
        assertEquals("zero", coordinator.bridgeDescribe(0))
        assertEquals("one", coordinator.bridgeDescribe(1))
        assertEquals("small", coordinator.bridgeDescribe(5))
        assertEquals("large", coordinator.bridgeDescribe(100))
    }
}
