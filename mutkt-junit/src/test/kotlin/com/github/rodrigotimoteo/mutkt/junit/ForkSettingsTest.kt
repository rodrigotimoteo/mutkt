package com.github.rodrigotimoteo.mutkt.junit

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Path

class ForkSettingsTest {
    private fun settings(
        engines: List<String> = listOf("junit-jupiter"),
        includeTags: List<String> = emptyList(),
        excludeTags: List<String> = emptyList(),
    ) = ForkSettings(
        Path.of("java"),
        emptyList(),
        emptyMap(),
        null,
        Path.of("main"),
        Path.of("test"),
        emptyList(),
        1,
        engines = engines,
        includeTags = includeTags,
        excludeTags = excludeTags,
    )

    private fun message(block: () -> Unit) = assertThrows<IllegalArgumentException> { block() }.message.orEmpty()

    @Test
    fun `tag expressions with line breaks name their setting`() {
        assertTrue("includeTags" in message { settings(includeTags = listOf("a\nb")) })
        assertTrue("excludeTags" in message { settings(excludeTags = listOf("a\rb")) })
    }

    @Test
    fun `paths written to the startup file reject line breaks`() {
        val broken = Path.of("a\nb")
        val expected = "must not contain line breaks: [a\nb]"
        assertTrue(expected in message { settings().copy(projectClassDir = broken) })
        assertTrue(expected in message { settings().copy(testClassDir = broken) })
        assertTrue(expected in message { settings().copy(classpath = listOf(Path.of("ok.jar"), broken)) })
        assertTrue(expected in message { settings().copy(codeSources = mapOf("Calc" to broken)) })
        assertTrue("a\rb" in message { settings().copy(codeSources = mapOf("a\rb" to Path.of("ok"))) })
    }

    @Test
    fun `a non-positive preparation limit names its setting`() {
        assertTrue("prepareMillis" in message { settings().copy(prepareMillis = 0) })
    }

    @Test
    fun `empty engines name the engines setting`() {
        assertTrue("'engines'" in message { settings(engines = emptyList()) })
    }
}
