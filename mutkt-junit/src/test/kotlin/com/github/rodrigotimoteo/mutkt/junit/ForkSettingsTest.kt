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
    fun `a non-positive preparation limit names its setting`() {
        assertTrue("prepareMillis" in message { settings().copy(prepareMillis = 0) })
    }

    @Test
    fun `empty engines name the engines setting`() {
        assertTrue("'engines'" in message { settings(engines = emptyList()) })
    }
}
