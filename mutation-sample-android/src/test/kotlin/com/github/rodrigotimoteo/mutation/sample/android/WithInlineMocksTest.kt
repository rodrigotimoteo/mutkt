package com.github.rodrigotimoteo.mutation.sample.android

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WithInlineMocksTest {

    @Test
    fun `mockk inline mock on final class works`() {
        val mockManager = mockk<SharedPreferencesManager>(relaxed = true)
        every { mockManager.getUsername() } returns "inline-mockk"
        assertEquals("inline-mockk", mockManager.getUsername())
        verify { mockManager.getUsername() }
    }

    @Test
    fun `mockk static method works`() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        android.util.Log.d("test", "message")
        verify { android.util.Log.d("test", "message") }
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `mockito inline mock of final class works`() {
        val mockManager = mock<SharedPreferencesManager>()
        whenever(mockManager.getUsername()).thenReturn("inline-mockito")
        assertEquals("inline-mockito", mockManager.getUsername())
    }
}
