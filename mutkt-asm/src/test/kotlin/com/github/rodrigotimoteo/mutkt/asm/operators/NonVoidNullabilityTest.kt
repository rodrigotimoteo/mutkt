package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Hierarchy
import com.github.rodrigotimoteo.mutkt.asm.NoClasses
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

private val SOURCE =
    """
    fun name(): String = "n"
    fun maybe(): String? = "m"
    fun <T> ident(a: T): T = a
    fun <T : Any> identNonNull(a: T): T = a
    fun withDefault(a: String = "d"): String = a
    fun count(): Int = 1
    open class Base { open fun label(): String = "b" }
    class Sub : Base()
    interface Namer { fun nameOf(): String }

    fun callName(): String? = name()
    fun callDefault(): String? = withDefault()
    fun callInherited(b: Sub): String? = b.label()
    fun callInterface(n: Namer): String? = n.nameOf()
    fun callIdentNonNull(): String? = identNonNull("x")
    fun callStdlib(s: String): String? = s.repeat(2)
    fun callMaybe(): String? = maybe()
    fun callIdent(): String? = ident("i")
    fun callPlatform(): String? = java.lang.String.valueOf(7)
    fun callCount(): Int = count()
    """.trimIndent()

class NonVoidNullabilityTest {
    private val classes = compileKotlin(SOURCE)
    private val file = classes.getValue("SourceKt")

    // The compiled classes first, then the test classpath (Kotlin stdlib), as the engine's hierarchy does.
    private val hierarchy: Hierarchy = { type ->
        classes[type.replace('/', '.')]
            ?: javaClass.classLoader.getResourceAsStream("$type.class")?.use { it.readBytes() }
    }

    private fun sites(
        method: String,
        lookup: Hierarchy = hierarchy,
    ) = discover(file, NonVoidMethodCallsOperator, hierarchy = lookup).filter { it.methodName == method }

    @ParameterizedTest
    @ValueSource(
        strings = ["callName", "callDefault", "callInherited", "callInterface", "callIdentNonNull", "callStdlib"],
    )
    fun `a call of a method declared non-null is not replaced by null`(method: String) {
        assertEquals(emptyList<String>(), sites(method).map { it.description })
    }

    @ParameterizedTest
    @ValueSource(strings = ["callMaybe", "callIdent", "callPlatform"])
    fun `a nullable, generic or Java result becomes null`(method: String) {
        val found = sites(method).single()
        val changed = classes + ("SourceKt" to mutate(file, NonVoidMethodCallsOperator, found, hierarchy))

        assertNotNull(loadAndInvoke(classes, "SourceKt", method))
        assertNull(loadAndInvoke(changed, "SourceKt", method))
    }

    @Test
    fun `a primitive result keeps its zero default`() {
        assertEquals(listOf("replace call with default: SourceKt.count"), sites("callCount").map { it.description })
    }

    @Test
    fun `a callee the hierarchy does not know stays a site`() {
        assertEquals(1, sites("callName", NoClasses).size)
    }
}
