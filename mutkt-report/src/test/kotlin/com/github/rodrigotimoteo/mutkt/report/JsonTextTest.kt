package com.github.rodrigotimoteo.mutkt.report

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class JsonTextTest {
    private val unicode = "\\" + "u"
    private val shortEscapes = mapOf(0x08 to "b", 0x09 to "t", 0x0A to "n", 0x0C to "f", 0x0D to "r")

    @Test
    fun `every control character is escaped and round-trips`() {
        for (code in 0..0x1F) {
            val text = "a" + code.toChar() + "b"
            val escape = shortEscapes[code]?.let { "\\" + it } ?: (unicode + "%04x").format(code)

            val json = renderJson(text)

            assertEquals("\"a" + escape + "b\"", json, "U+" + code)
            assertEquals(text, ObjectMapper().readTree(json).asText(), "U+" + code)
        }
    }

    @Test
    fun `quotes, backslashes and line separators are escaped`() {
        assertEquals("\"\\\"q\\\\\"", renderJson("\"q\\"))
        assertEquals("\"" + unicode + "2028" + unicode + "2029\"", renderJson(Char(0x2028).toString() + Char(0x2029)))
    }

    @Test
    fun `other text, surrogate pairs and slashes stay as they are`() {
        val text = "caf" + Char(0xE9) + " " + Char(0xD83D) + Char(0xDE00) + " </script> a/b"
        assertEquals("\"" + text + "\"", renderJson(text))
    }

    @Test
    fun `values render as pretty JSON`() {
        val json =
            renderJson(
                mapOf(
                    "a" to listOf(1, true, null),
                    "b" to emptyMap<String, Any>(),
                    "c" to emptyList<Int>(),
                ),
            )

        assertEquals("{\n  \"a\": [\n    1,\n    true,\n    null\n  ],\n  \"b\": {},\n  \"c\": []\n}", json)
    }

    @Test
    fun `an unsupported value is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { renderJson(Any()) }
    }
}
