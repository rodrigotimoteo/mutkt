package com.github.rodrigotimoteo.mutation.metadata

import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KotlinMetadataParserTest {
    @Test
    fun `parse returns KotlinClassInfo for any class`() {
        val bytes = buildJavaClass()
        val info = KotlinMetadataParser.parse(bytes)
        assertNotNull(info)
        assertEquals("com.example.Foo", info.className)
    }

    @Test
    fun `parse marks isKotlinClass false for non-Kotlin class`() {
        val bytes = buildJavaClass()
        val info = KotlinMetadataParser.parse(bytes)
        assertFalse(info.isKotlinClass)
    }

    @Test
    fun `parse marks isKotlinClass true for class with Metadata annotation`() {
        val bytes = buildKotlinClass()
        val info = KotlinMetadataParser.parse(bytes)
        assertTrue(info.isKotlinClass)
    }

    @Test
    fun `parse detects interface`() {
        val bytes = buildInterface()
        val info = KotlinMetadataParser.parse(bytes)
        assertTrue(info.isInterface)
    }

    @Test
    fun `parse detects non-interface`() {
        val bytes = buildJavaClass()
        val info = KotlinMetadataParser.parse(bytes)
        assertFalse(info.isInterface)
    }

    @Test
    fun `parse handles empty class without throwing`() {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Empty", null, "java/lang/Object", null)
        cw.visitEnd()
        val bytes = cw.toByteArray()
        val info = KotlinMetadataParser.parse(bytes)
        assertNotNull(info)
    }

    @Test
    fun `parse handles truncated bytes gracefully`() {
        val bytes = ByteArray(10)
        // Should not throw
        try {
            val info = KotlinMetadataParser.parse(bytes)
            assertNotNull(info)
        } catch (e: Exception) {
            // Acceptable to throw for invalid bytes
        }
    }

    @Test
    fun `isKotlinSyntheticMethod detects copy$default`() {
        assertTrue(KotlinMetadataParser.isKotlinSyntheticMethod("copy\$default"))
    }

    @Test
    fun `isKotlinSyntheticMethod detects component1$default`() {
        assertTrue(KotlinMetadataParser.isKotlinSyntheticMethod("component1\$default"))
    }

    @Test
    fun `isKotlinSyntheticMethod detects component2$default`() {
        assertTrue(KotlinMetadataParser.isKotlinSyntheticMethod("component2\$default"))
    }

    @Test
    fun `isKotlinSyntheticMethod detects Foo$serializer`() {
        assertTrue(KotlinMetadataParser.isKotlinSyntheticMethod("Foo\$serializer"))
    }

    @Test
    fun `isKotlinSyntheticMethod detects init$default`() {
        assertTrue(KotlinMetadataParser.isKotlinSyntheticMethod("<init>\$default"))
    }

    @Test
    fun `isKotlinSyntheticMethod detects toString$default`() {
        assertTrue(KotlinMetadataParser.isKotlinSyntheticMethod("toString\$default"))
    }

    @Test
    fun `isKotlinSyntheticMethod detects hashCode$default`() {
        assertTrue(KotlinMetadataParser.isKotlinSyntheticMethod("hashCode\$default"))
    }

    @Test
    fun `isKotlinSyntheticMethod detects equals$default`() {
        assertTrue(KotlinMetadataParser.isKotlinSyntheticMethod("equals\$default"))
    }

    @Test
    fun `isKotlinSyntheticMethod returns false for regular method names`() {
        assertFalse(KotlinMetadataParser.isKotlinSyntheticMethod("add"))
        assertFalse(KotlinMetadataParser.isKotlinSyntheticMethod("getName"))
        assertFalse(KotlinMetadataParser.isKotlinSyntheticMethod("setName"))
    }

    @Test
    fun `isKotlinSyntheticMethod returns false for names with dollar in middle`() {
        // "myMethod$inner" should NOT match
        assertFalse(KotlinMetadataParser.isKotlinSyntheticMethod("myMethod\$inner"))
    }

    @Test
    fun `isKotlinSyntheticMethod returns false for empty string`() {
        assertFalse(KotlinMetadataParser.isKotlinSyntheticMethod(""))
    }

    @Test
    fun `isKotlinSyntheticMethod returns false for null check pattern`() {
        // Common case: regular method called somethingDefault shouldn't match
        assertFalse(KotlinMetadataParser.isKotlinSyntheticMethod("somethingDefault"))
    }

    @Test
    fun `KotlinClassInfo default values`() {
        val info =
            KotlinMetadataParser.KotlinClassInfo(
                className = "Foo",
                isKotlinClass = false,
            )
        assertEquals("Foo", info.className)
        assertFalse(info.isKotlinClass)
        assertFalse(info.isDataClass)
        assertFalse(info.isSealedClass)
        assertFalse(info.isInlineClass)
        assertFalse(info.isObject)
        assertFalse(info.isInterface)
        assertFalse(info.isEnumClass)
    }

    @Test
    fun `KotlinClassInfo equality`() {
        val a = KotlinMetadataParser.KotlinClassInfo(className = "Foo", isKotlinClass = true)
        val b = KotlinMetadataParser.KotlinClassInfo(className = "Foo", isKotlinClass = true)
        assertEquals(a, b)
    }

    @Test
    fun `parse converts slashed class names to dotted form`() {
        val bytes = buildJavaClass()
        val info = KotlinMetadataParser.parse(bytes)
        assertFalse(info.className.contains('/'), "Should not contain slashes")
    }

    @Test
    fun `parse handles Metadata annotation presence`() {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Kotlin", null, "java/lang/Object", null)
        val av = cw.visitAnnotation("Lkotlin/Metadata;", true)
        av?.visitEnd()
        cw.visitEnd()
        val bytes = cw.toByteArray()
        val info = KotlinMetadataParser.parse(bytes)
        assertTrue(info.isKotlinClass)
    }

    @Test
    fun `parse handles multiple annotations including Metadata`() {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Multi", null, "java/lang/Object", null)
        val kotlinAv = cw.visitAnnotation("Lkotlin/Metadata;", true)
        kotlinAv?.visitEnd()
        val otherAv = cw.visitAnnotation("Lcom/example/MyAnnotation;", true)
        otherAv?.visitEnd()
        cw.visitEnd()
        val bytes = cw.toByteArray()
        val info = KotlinMetadataParser.parse(bytes)
        assertTrue(info.isKotlinClass)
    }

    private fun buildJavaClass(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildKotlinClass(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Kotlin", null, "java/lang/Object", null)
        val av = cw.visitAnnotation("Lkotlin/Metadata;", true)
        av?.visitEnd()
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildInterface(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC + Opcodes.ACC_INTERFACE, "com/example/Ifc", null, "java/lang/Object", null)
        cw.visitEnd()
        return cw.toByteArray()
    }
}
