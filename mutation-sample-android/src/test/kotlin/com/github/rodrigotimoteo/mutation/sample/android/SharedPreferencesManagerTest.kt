package com.github.rodrigotimoteo.mutation.sample.android

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedPreferencesManagerTest {

    private lateinit var manager: SharedPreferencesManager

    @Before
    fun setUp() {
        manager = SharedPreferencesManager(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `default username is null`() {
        assertNull(manager.getUsername())
    }

    @Test
    fun `set and get username`() {
        manager.setUsername("alice")
        assertEquals("alice", manager.getUsername())
    }

    @Test
    fun `isLoggedIn returns false when no username`() {
        assertFalse(manager.isLoggedIn())
    }

    @Test
    fun `isLoggedIn returns true when username set`() {
        manager.setUsername("bob")
        assertTrue(manager.isLoggedIn())
    }

    @Test
    fun `increment login count from zero`() {
        manager.incrementLoginCount()
        assertEquals(1, manager.getLoginCount())
    }

    @Test
    fun `increment login count multiple times`() {
        manager.incrementLoginCount()
        manager.incrementLoginCount()
        manager.incrementLoginCount()
        assertEquals(3, manager.getLoginCount())
    }

    @Test
    fun `clear removes all preferences`() {
        manager.setUsername("temp")
        manager.clear()
        assertNull(manager.getUsername())
    }
}
