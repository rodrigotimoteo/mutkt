package com.github.rodrigotimoteo.mutation.annotation

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MutKtTestAnnotationTest {
    @Test
    fun `annotation default mode is LENIENT`() {
        val ann = SampleTest::class.java.getAnnotation(MutKtTest::class.java)
        assertEquals(VerificationMode.LENIENT, ann?.mode)
    }

    @Test
    fun `annotation default timeoutMs is 30_000`() {
        val ann = SampleTest::class.java.getAnnotation(MutKtTest::class.java)
        assertEquals(30_000L, ann?.timeoutMs)
    }

    @Test
    fun `annotation can override defaults`() {
        val ann = CustomTest::class.java.getAnnotation(MutKtTest::class.java)
        assertEquals(VerificationMode.STRICT, ann?.mode)
        assertEquals(60_000L, ann?.timeoutMs)
        assertTrue(ann?.skipInIDE == false)
    }

    @Test
    fun `annotation has RUNTIME retention`() {
        val retention = MutKtTest::class.java.getAnnotation(Retention::class.java)
        assertNotNull(retention)
        val str = retention.toString()
        assertTrue(str.contains("RUNTIME"), "Expected RUNTIME in: $str")
    }

    @Test
    fun `annotation has CLASS target`() {
        val target = MutKtTest::class.java.getAnnotation(Target::class.java)
        val targets = target?.let { it.allowedTargets.toList() } ?: emptyList()
        assertTrue(targets.contains(AnnotationTarget.CLASS))
    }
}

@MutKtTest
private class SampleTest

@MutKtTest(
    mode = VerificationMode.STRICT,
    timeoutMs = 60_000L,
    skipInIDE = false,
)
private class CustomTest
