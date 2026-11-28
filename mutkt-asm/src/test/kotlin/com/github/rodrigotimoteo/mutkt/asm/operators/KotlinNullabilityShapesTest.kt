package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.asm.parse
import com.github.rodrigotimoteo.mutkt.testing.buildClass
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes.ARETURN

// Kotlin shapes whose non-null String returns are decided by kotlinc's annotations: generated members, delegates,
// value-class mangling, lateinit getters. Each line is "operator Class.method description".
private val SOURCE =
    """
    @file:JvmMultifileClass
    @file:JvmName("Util")

    fun util(): String = "u"

    data class Point(val x: Int, val label: String)

    class Late { lateinit var title: String }

    @JvmInline value class Id(val raw: String) { init { require(raw.isNotEmpty()) } }
    fun rawOf(id: Id): String = id.raw

    class Owner { companion object { @JvmStatic fun make(): String = "m" } }

    interface Named { fun name(): String = "n" }
    """.trimIndent()

class KotlinNullabilityShapesTest {
    private val classes = compileKotlin(SOURCE)

    private fun mutants(vararg names: String): List<String> =
        names.flatMap { cls ->
            listOf(NullReturnsOperator, EmptyReturnsOperator).flatMap { op ->
                discover(classes.getValue(cls), op).map { "${op.id.value} $cls.${it.methodName} ${it.description}" }
            }
        }

    @Test
    fun `a multifile facade and its part both return ""`() {
        assertEquals(
            listOf("empty-returns Util.util areturn -> \"\"", "empty-returns Util__SourceKt.util areturn -> \"\""),
            mutants("Util", "Util__SourceKt"),
        )
    }

    @Test
    fun `generated data class members get no mutant, the property getter gets ""`() {
        assertEquals(listOf("empty-returns Point.getLabel areturn -> \"\""), mutants("Point"))
    }

    @Test
    fun `a lateinit getter has one "" mutant, none on the unreachable return after its throw`() {
        assertEquals(listOf("empty-returns Late.getTitle areturn -> \"\""), mutants("Late"))
    }

    @Test
    fun `value-class-mangled methods get no "" mutant`() {
        val mangled =
            listOf("Id", "Util__SourceKt").flatMap { cls ->
                val methods = parse(classes.getValue(cls)).methods
                methods.filter { '-' in it.name && it.desc.endsWith(")Ljava/lang/String;") }
            }

        assertEquals(
            setOf("constructor", "toString", "unbox", "rawOf"),
            mangled.map { it.name.substringBefore("-") }.toSet(),
        )
        val emptyOnMangled =
            mutants(
                "Id",
                "Util__SourceKt",
            ).filter { it.startsWith("empty-") && '-' in it.split(' ')[1] }
        assertEquals(emptyList<String>(), emptyOnMangled)
    }

    @Test
    fun `a JvmStatic companion function and its static delegate both return ""`() {
        assertEquals(
            listOf("empty-returns Owner.make areturn -> \"\"", "empty-returns Owner\$Companion.make areturn -> \"\""),
            mutants("Owner", "Owner\$Companion"),
        )
    }

    @Test
    fun `an interface default body and its DefaultImpls delegate both return ""`() {
        assertEquals(
            listOf(
                "empty-returns Named.name areturn -> \"\"",
                "empty-returns Named\$DefaultImpls.name areturn -> \"\"",
            ),
            mutants("Named", "Named\$DefaultImpls"),
        )
    }

    @Test
    fun `a NotNull annotation outside Kotlin is not trusted, so the Java method returns null`() {
        val java =
            buildClass("JavaNotNull", "name", "()Ljava/lang/String;") {
                visitAnnotation("Lorg/jetbrains/annotations/NotNull;", false)
                visitLdcInsn("j")
                visitInsn(ARETURN)
            }
        val found = discover(java, NullReturnsOperator).single()

        assertEquals(emptyList<Any>(), discover(java, EmptyReturnsOperator))
        val changed = mutate(java, NullReturnsOperator, found) { null }

        assertEquals("j", loadAndInvoke(mapOf("JavaNotNull" to java), "JavaNotNull", "name"))
        assertNull(loadAndInvoke(mapOf("JavaNotNull" to changed), "JavaNotNull", "name"))
    }
}
