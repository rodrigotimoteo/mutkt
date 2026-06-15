package com.github.rodrigotimoteo.mutation.sample.android

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResourceHelperTest {

    private lateinit var helper: ResourceHelper

    @Before
    fun setUp() {
        helper = ResourceHelper(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `formatCount returns None for zero`() {
        assertEquals("None", helper.formatCount(0))
    }

    @Test
    fun `formatCount returns singular for one`() {
        assertEquals("1 item", helper.formatCount(1))
    }

    @Test
    fun `formatCount returns plural for small numbers`() {
        assertEquals("5 items", helper.formatCount(5))
    }

    @Test
    fun `formatCount returns Many for large numbers`() {
        assertEquals("Many", helper.formatCount(100))
    }
}
