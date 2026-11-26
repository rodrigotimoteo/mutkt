package com.example.mutkt.android

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GreeterTest {
    @Test
    fun greetsByName() {
        assertEquals("Hello, Ada!", Greeter(RuntimeEnvironment.getApplication()).greet("Ada"))
    }
}
