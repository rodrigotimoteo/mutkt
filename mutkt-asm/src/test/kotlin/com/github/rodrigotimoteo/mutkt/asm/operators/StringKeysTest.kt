package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.testing.buildClass
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.ARETURN
import org.objectweb.asm.Opcodes.INVOKESTATIC

private val KEYS =
    """
    enum class Color { RED }
    fun color() = enumValueOf<Color>("RED")
    fun colorJava(): Color = java.lang.Enum.valueOf(Color::class.java, "RED")
    fun read(m: Map<String, Int>) = (m["k"] ?: 0) + m.getOrDefault("d", 1)
    fun has(m: Map<String, Int>) = m.containsKey("k")
    fun store(m: MutableMap<String, String>) { m.put("k", "value") }
    fun property() = System.getProperty("key", "fallback")
    """.trimIndent()

// Stand-ins for the Android API: only owner, name and descriptor matter to the operator.
private val CONTENT =
    """
    package android.content

    interface SharedPreferences {
        fun getString(key: String, defValue: String?): String?
        fun edit(): Editor
        interface Editor {
            fun putString(key: String, value: String?): Editor
            fun remove(key: String): Editor
        }
    }
    open class Intent {
        fun putExtra(name: String, value: String?): Intent = this
        fun getStringExtra(name: String): String? = null
    }
    fun prefs(p: SharedPreferences) = p.getString("name", "anonymous")
    fun save(p: SharedPreferences) { p.edit().putString("name", "bob").remove("old") }
    fun intent(i: Intent) = i.putExtra("id", "value").getStringExtra("id")
    """.trimIndent()

private val OS =
    """
    package android.os

    class Bundle {
        fun putString(key: String, value: String?) {}
        fun getString(key: String): String? = null
    }
    fun bundle(b: Bundle): String? {
        b.putString("k", "v")
        return b.getString("k")
    }
    """.trimIndent()

class StringKeysTest {
    private fun descriptions(
        classes: Map<String, ByteArray>,
        cls: String,
        method: String,
    ) = discover(classes.getValue(cls), StringsOperator).filter { it.methodName == method }.map { it.description }

    @Test
    fun `enum names and map keys are not sites, values passed with them are`() {
        val classes = compileKotlin(KEYS)
        val sites =
            listOf("color", "colorJava", "read", "has", "store", "property").associateWith {
                descriptions(classes, "SourceKt", it)
            }

        assertEquals(
            mapOf(
                "color" to emptyList(),
                "colorJava" to emptyList(),
                "read" to emptyList(),
                "has" to emptyList(),
                "store" to listOf("\"value\" -> \"\""),
                "property" to listOf("\"fallback\" -> \"\""),
            ),
            sites,
        )
    }

    @Test
    fun `android preference, extra and bundle keys are not sites`() {
        val content = compileKotlin(CONTENT)
        val os = compileKotlin(OS)

        assertEquals(listOf("\"anonymous\" -> \"\""), descriptions(content, "android.content.SourceKt", "prefs"))
        assertEquals(listOf("\"bob\" -> \"\""), descriptions(content, "android.content.SourceKt", "save"))
        assertEquals(listOf("\"value\" -> \"\""), descriptions(content, "android.content.SourceKt", "intent"))
        assertEquals(listOf("\"v\" -> \"\""), descriptions(os, "android.os.SourceKt", "bundle"))
    }

    // Kotlin before 1.4 concatenated a String? through Intrinsics.stringPlus; its text is mutated like any other.
    @Test
    fun `a literal given to Intrinsics stringPlus is a site`() {
        val plain =
            buildClass("Plus", "plus", "(Ljava/lang/Object;)Ljava/lang/String;") {
                visitLdcInsn("a")
                visitVarInsn(ALOAD, 0)
                visitMethodInsn(
                    INVOKESTATIC,
                    "kotlin/jvm/internal/Intrinsics",
                    "stringPlus",
                    "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;",
                    false,
                )
                visitInsn(ARETURN)
            }
        val found = discover(plain, StringsOperator).single()
        val bytes = mutate(plain, StringsOperator, found) { null }

        assertEquals("\"a\" -> \"\"", found.description)
        assertEquals(
            "a1" to "1",
            loadAndInvoke(mapOf("Plus" to plain), "Plus", "plus", 1) to
                loadAndInvoke(mapOf("Plus" to bytes), "Plus", "plus", 1),
        )
    }
}
