package com.github.rodrigotimoteo.mutkt.asm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.V17

class HierarchyClassWriterTest {
    private val none: Hierarchy = { null }

    // Stands in for the runtime classpath, which is where the Gradle wiring will read dependency classes from.
    private val classpath: Hierarchy = {
        ClassLoader.getSystemResourceAsStream("$it.class")?.use { s ->
            s.readBytes()
        }
    }

    private fun common(
        a: String,
        b: String,
        hierarchy: Hierarchy = none,
    ) = HierarchyClassWriter(hierarchy).getCommonSuperClass(a, b)

    @Test
    fun `JDK types resolve from the platform loader without a lookup`() {
        assertEquals("java/util/AbstractList", common("java/util/ArrayList", "java/util/LinkedList"))
        assertEquals("java/lang/Number", common("java/lang/Integer", "java/lang/Long"))
        assertEquals("java/lang/Object", common("java/lang/String", "java/lang/Integer"))
    }

    @Test
    fun `a type and its own ancestor merge to the ancestor`() {
        assertEquals("java/util/AbstractList", common("java/util/AbstractList", "java/util/ArrayList"))
    }

    @Test
    fun `interfaces merge to Object`() {
        assertEquals("java/lang/Object", common("java/util/List", "java/util/ArrayList"))
        assertEquals("java/lang/Object", common("java/util/List", "java/util/Set"))
    }

    @Test
    fun `dependency types need the lookup`() {
        val stdlib = "kotlin/collections/ArrayDeque"

        assertEquals("java/util/AbstractList", common(stdlib, "java/util/ArrayList", classpath))
        val error = assertThrows<IllegalStateException> { common(stdlib, "java/util/ArrayList") }
        assertTrue(error.message!!.contains(stdlib), error.message)
    }

    @Test
    fun `a cyclic hierarchy fails instead of looping`() {
        fun header(
            name: String,
            superName: String,
        ) = ClassWriter(0).also { it.visit(V17, ACC_PUBLIC, name, null, superName, null) }.toByteArray()
        val cyclic: Hierarchy = { mapOf("A" to header("A", "B"), "B" to header("B", "A"))[it] }

        val error = assertThrows<IllegalStateException> { common("A", "java/lang/String", cyclic) }

        assertTrue(error.message!!.contains("Cyclic"), error.message)
    }
}
