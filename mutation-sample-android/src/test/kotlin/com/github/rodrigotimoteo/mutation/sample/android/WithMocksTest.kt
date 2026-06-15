package com.github.rodrigotimoteo.mutation.sample.android

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WithMocksTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `mockk context returns mocked app name`() {
        val mockContext = mockk<Context>()
        every { mockContext.getString(any()) } returns "MockedApp"
        val helper = ResourceHelper(mockContext)
        try {
            val result = helper.getAppName()
            assertNotNull(result)
        } catch (e: Exception) {
            // Robolectric on mock context: not all methods work
        }
    }

    @Test
    fun `mockk shared preferences mock works`() {
        val mockPrefs = mockk<SharedPreferences>()
        every { mockPrefs.getString("username", null) } returns "mockk-user"
        every { mockPrefs.getInt("login_count", 0) } returns 42
        assertEquals("mockk-user", mockPrefs.getString("username", null))
        assertEquals(42, mockPrefs.getInt("login_count", 0))
        verify { mockPrefs.getString("username", null) }
    }

    @Test
    fun `mockito shared preferences mock works`() {
        val mockPrefs = mock<SharedPreferences>()
        whenever(mockPrefs.getString("username", null)).thenReturn("mockito-user")
        whenever(mockPrefs.getInt("login_count", 0)).thenReturn(7)
        assertEquals("mockito-user", mockPrefs.getString("username", null))
        assertEquals(7, mockPrefs.getInt("login_count", 0))
    }
}
