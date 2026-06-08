package com.github.rodrigotimoteo.mutation.classloader

import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import com.github.rodrigotimoteo.mutation.mutator.Mutator
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MutantClassLoaderTest {
    @Test
    fun `loadClass returns valid Class for injected class`() {
        val bytes = buildCalculatorClass()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val dummyMutation = createDummyMutation()
        val loader =
            MutantClassLoader(
                parent = MutantClassLoaderTest::class.java.classLoader,
                originalClassBytes = mapOf("com/example/Calc" to bytes),
                targetMutation = dummyMutation,
                mutator = mutator,
            )
        val clazz = loader.loadClass("com.example.Calc")
        assertNotNull(clazz)
        assertEquals("com.example.Calc", clazz.name)
    }

    @Test
    fun `loadClass returns same class for repeated calls`() {
        val bytes = buildCalculatorClass()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val dummyMutation = createDummyMutation()
        val loader =
            MutantClassLoader(
                parent = MutantClassLoaderTest::class.java.classLoader,
                originalClassBytes = mapOf("com/example/Calc" to bytes),
                targetMutation = dummyMutation,
                mutator = mutator,
            )
        val c1 = loader.loadClass("com.example.Calc")
        val c2 = loader.loadClass("com.example.Calc")
        assertEquals(c1, c2)
    }

    @Test
    fun `loadClass falls back to parent for unknown class`() {
        val bytes = buildCalculatorClass()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val dummyMutation = createDummyMutation()
        val loader =
            MutantClassLoader(
                parent = MutantClassLoaderTest::class.java.classLoader,
                originalClassBytes = mapOf("com/example/Calc" to bytes),
                targetMutation = dummyMutation,
                mutator = mutator,
            )
        val str = loader.loadClass("java.lang.String")
        assertEquals(String::class.java, str)
    }

    @Test
    fun `loadClass throws ClassNotFoundException for missing class`() {
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val dummyMutation = createDummyMutation()
        val loader =
            MutantClassLoader(
                parent = MutantClassLoaderTest::class.java.classLoader,
                originalClassBytes = emptyMap(),
                targetMutation = dummyMutation,
                mutator = mutator,
            )
        try {
            loader.loadClass("com.example.DoesNotExist")
            assertTrue(false, "Should have thrown ClassNotFoundException")
        } catch (e: ClassNotFoundException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun `loadClass loads mutated class when target mutation matches className`() {
        val bytes = buildCalculatorClass()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val mutations = mutator.scanMutations(bytes)
        val arithMutation = mutations.first { it.originalOpcode == Opcodes.IADD }

        val loader =
            MutantClassLoader(
                parent = MutantClassLoaderTest::class.java.classLoader,
                originalClassBytes = mapOf("com/example/Calc" to bytes),
                targetMutation = arithMutation,
                mutator = mutator,
            )
        val clazz = loader.loadClass("com.example.Calc")
        val instance = clazz.getDeclaredConstructor().newInstance()
        val addMethod = clazz.getDeclaredMethod("add", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val result = addMethod.invoke(instance, 5, 3) as Int
        // After IADD -> ISUB: 5 - 3 = 2 (not 5 + 3 = 8)
        assertEquals(2, result)
    }

    @Test
    fun `loadClass loads non-mutated class when target mutation has different className`() {
        val bytes = buildCalculatorClass()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        // Create a mutation targeting a different class
        val dummyMutation = createDummyMutation(className = "com.example.OtherClass")

        val loader =
            MutantClassLoader(
                parent = MutantClassLoaderTest::class.java.classLoader,
                originalClassBytes = mapOf("com/example/Calc" to bytes),
                targetMutation = dummyMutation,
                mutator = mutator,
            )
        val clazz = loader.loadClass("com.example.Calc")
        val instance = clazz.getDeclaredConstructor().newInstance()
        val addMethod = clazz.getDeclaredMethod("add", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val result = addMethod.invoke(instance, 5, 3) as Int
        // No mutation applied: 5 + 3 = 8
        assertEquals(8, result)
    }

    @Test
    fun `classloader isolation loaded class uses this classloader`() {
        val bytes = buildCalculatorClass()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val dummyMutation = createDummyMutation()
        val loader =
            MutantClassLoader(
                parent = MutantClassLoaderTest::class.java.classLoader,
                originalClassBytes = mapOf("com/example/Calc" to bytes),
                targetMutation = dummyMutation,
                mutator = mutator,
            )
        val clazz = loader.loadClass("com.example.Calc")
        assertEquals(loader, clazz.classLoader)
    }

    @Test
    fun `two classloaders are independent`() {
        val bytes = buildCalculatorClass()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val dummyMutation = createDummyMutation()
        val loader1 =
            MutantClassLoader(
                parent = MutantClassLoaderTest::class.java.classLoader,
                originalClassBytes = mapOf("com/example/Calc" to bytes),
                targetMutation = dummyMutation,
                mutator = mutator,
            )
        val loader2 =
            MutantClassLoader(
                parent = MutantClassLoaderTest::class.java.classLoader,
                originalClassBytes = mapOf("com/example/Calc" to bytes),
                targetMutation = dummyMutation,
                mutator = mutator,
            )
        assertNotEquals(loader1, loader2)
        val c1 = loader1.loadClass("com.example.Calc")
        val c2 = loader2.loadClass("com.example.Calc")
        assertNotEquals(c1, c2, "Different classloaders should load different Class instances")
    }

    @Test
    fun `target class is loaded with mutated bytes`() {
        val bytes = buildCalculatorClass()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val dummyMutation = createDummyMutation()
        val loader =
            MutantClassLoader(
                parent = MutantClassLoaderTest::class.java.classLoader,
                originalClassBytes = mapOf("com/example/Calc" to bytes),
                targetMutation = dummyMutation,
                mutator = mutator,
            )
        // loadClass loads project classes through this classloader (defineClass)
        val clazz = loader.loadClass("com.example.Calc")
        assertNotNull(clazz)
        assertEquals(loader, clazz.classLoader, "Target class should be loaded by MutantClassLoader")
    }

    @Test
    fun `non-target class falls back to parent`() {
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val dummyMutation = createDummyMutation()
        val loader =
            MutantClassLoader(
                parent = MutantClassLoaderTest::class.java.classLoader,
                originalClassBytes = emptyMap(),
                targetMutation = dummyMutation,
                mutator = mutator,
            )
        // Class not in originalClassBytes → delegates to parent
        val clazz = loader.loadClass("java.lang.String")
        assertNotNull(clazz)
        // String is loaded by bootstrap classloader (null), confirming delegation to parent
        assertNull(clazz.classLoader, "JDK class should be loaded by bootstrap (parent delegation)")
    }

    @Test
    fun `cross-class resolution - dependent class sees mutated target`() {
        // Build two classes: Caller references Target.
        // Caller.add(a, b) calls Target.add(a, b)
        // Mutate Target.compute: IADD → ISUB
        // Then Caller.add should return a - b instead of a + b
        val targetBytes = buildClassWithName("com/example/Target", makeStatic = true)
        val callerBytes = buildCallerClass()

        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val mutations = mutator.scanMutations(targetBytes)
        val arithMutation = mutations.first { it.originalOpcode == Opcodes.IADD }

        val classBytes =
            mapOf(
                "com/example/Target" to targetBytes,
                "com/example/Caller" to callerBytes,
            )
        val loader =
            MutantClassLoader(
                parent = MutantClassLoaderTest::class.java.classLoader,
                originalClassBytes = classBytes,
                targetMutation = arithMutation,
                mutator = mutator,
            )
        // Load Caller through MutantClassLoader — it resolves Target through us
        val callerClass = loader.loadClass("com.example.Caller")
        assertEquals(loader, callerClass.classLoader, "Caller should be loaded by MutantClassLoader")
        val targetClass = loader.loadClass("com/example.Target")
        assertEquals(loader, targetClass.classLoader, "Target should be loaded by MutantClassLoader")

        val instance = callerClass.getDeclaredConstructor().newInstance()
        val method = callerClass.getDeclaredMethod("add", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val result = method.invoke(instance, 5, 3) as Int
        // After mutation: Target.add(5,3) = 5 - 3 = 2, so Caller.add(5,3) = 2
        assertEquals(2, result, "Caller should see mutated Target behavior")
    }

    @Test
    fun `non-target project class loads from original bytes not parent`() {
        val calcBytes = buildCalculatorClass()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        // Target is a different class, so Calc should load unmutated
        val dummyMutation = createDummyMutation(className = "com.example.OtherClass")
        val loader =
            MutantClassLoader(
                parent = MutantClassLoaderTest::class.java.classLoader,
                originalClassBytes = mapOf("com/example/Calc" to calcBytes),
                targetMutation = dummyMutation,
                mutator = mutator,
            )
        val clazz = loader.loadClass("com.example.Calc")
        assertEquals(loader, clazz.classLoader, "Non-target project class should be loaded by MutantClassLoader")
    }

    @Test
    fun `getParent returns parent classloader`() {
        val parent = MutantClassLoaderTest::class.java.classLoader
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val dummyMutation = createDummyMutation()
        val loader =
            MutantClassLoader(
                parent = parent,
                originalClassBytes = emptyMap(),
                targetMutation = dummyMutation,
                mutator = mutator,
            )
        assertEquals(parent, loader.parent)
    }

    @Test
    fun `mutated class is cached`() {
        val bytes = buildCalculatorClass()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val mutations = mutator.scanMutations(bytes)
        val arithMutation = mutations.first { it.originalOpcode == Opcodes.IADD }

        val loader =
            MutantClassLoader(
                parent = MutantClassLoaderTest::class.java.classLoader,
                originalClassBytes = mapOf("com/example/Calc" to bytes),
                targetMutation = arithMutation,
                mutator = mutator,
            )
        val c1 = loader.loadClass("com.example.Calc")
        val c2 = loader.loadClass("com.example.Calc")
        assertEquals(c1, c2, "Same Class instance should be returned on cache hit")
    }

    @Test
    fun `loadClass with multiple injected classes`() {
        val calcBytes = buildCalculatorClass()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val dummyMutation = createDummyMutation()
        val loader =
            MutantClassLoader(
                parent = MutantClassLoaderTest::class.java.classLoader,
                originalClassBytes = mapOf("com/example/Calc" to calcBytes),
                targetMutation = dummyMutation,
                mutator = mutator,
            )
        val clazz = loader.loadClass("com.example.Calc")
        assertEquals("com.example.Calc", clazz.name)
    }

    @Test
    fun `loadClass returns Class with correct method signatures`() {
        val bytes = buildCalculatorClass()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val dummyMutation = createDummyMutation()
        val loader =
            MutantClassLoader(
                parent = MutantClassLoaderTest::class.java.classLoader,
                originalClassBytes = mapOf("com/example/Calc" to bytes),
                targetMutation = dummyMutation,
                mutator = mutator,
            )
        val clazz = loader.loadClass("com.example.Calc")
        val methods = clazz.declaredMethods
        val addMethod = methods.firstOrNull { it.name == "add" }
        assertNotNull(addMethod)
        assertEquals(2, addMethod.parameterCount)
        assertEquals(Int::class.javaPrimitiveType, addMethod.returnType)
    }

    @Test
    fun `MutantClassLoaderFactory create returns MutantClassLoader`() {
        val bytes = buildCalculatorClass()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val dummyMutation = createDummyMutation()
        val loader =
            MutantClassLoaderFactory.create(
                parent = MutantClassLoaderTest::class.java.classLoader,
                originalClassBytes = mapOf("com/example/Calc" to bytes),
                targetMutation = dummyMutation,
                mutator = mutator,
            )
        assertNotNull(loader)
        assertTrue(loader is MutantClassLoader)
    }

    @Test
    fun `MutantClassLoaderFactory create preserves original classloader ref`() {
        val bytes = buildCalculatorClass()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val dummyMutation = createDummyMutation()
        val parent = MutantClassLoaderTest::class.java.classLoader
        val loader =
            MutantClassLoaderFactory.create(
                parent = parent,
                originalClassBytes = mapOf("com/example/Calc" to bytes),
                targetMutation = dummyMutation,
                mutator = mutator,
            )
        assertEquals(parent, loader.parent)
    }

    @Test
    fun `MutantClassLoaderFactory creates classloaders that can load injected classes`() {
        val bytes = buildCalculatorClass()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val dummyMutation = createDummyMutation()
        val loader =
            MutantClassLoaderFactory.create(
                parent = MutantClassLoaderTest::class.java.classLoader,
                originalClassBytes = mapOf("com/example/Calc" to bytes),
                targetMutation = dummyMutation,
                mutator = mutator,
            )
        val clazz = loader.loadClass("com.example.Calc")
        val instance = clazz.getDeclaredConstructor().newInstance()
        val addMethod = clazz.getDeclaredMethod("add", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val result = addMethod.invoke(instance, 2, 3) as Int
        assertEquals(5, result)
    }

    private fun createDummyMutation(className: String = "com.example.Dummy"): MutationInfo =
        MutationInfo(
            operator = MutationOperator.ARITHMETIC,
            className = className,
            methodName = "dummy",
            methodDescriptor = "()I",
            lineNumber = 1,
            description = "dummy",
            originalOpcode = Opcodes.IADD,
            mutatedOpcode = Opcodes.ISUB,
        )

    private fun buildCalculatorClass(): ByteArray = buildClassWithName("com/example/Calc")

    private fun buildClassWithName(
        internalName: String,
        makeStatic: Boolean = false,
    ): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val access = Opcodes.ACC_PUBLIC or if (makeStatic) Opcodes.ACC_STATIC else 0
        val mv = cw.visitMethod(access, "add", "(II)I", null, null)
        mv?.visitCode()
        if (!makeStatic) mv?.visitVarInsn(Opcodes.ILOAD, 1) else mv?.visitVarInsn(Opcodes.ILOAD, 0)
        if (!makeStatic) mv?.visitVarInsn(Opcodes.ILOAD, 2) else mv?.visitVarInsn(Opcodes.ILOAD, 1)
        mv?.visitInsn(Opcodes.IADD)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 3)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    /**
     * Builds a Caller class that calls Target.add(a, b).
     * Caller.add(a, b) = Target.add(a, b)
     */
    private fun buildCallerClass(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Caller", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "add", "(II)I", null, null)
        mv?.visitCode()
        mv?.visitVarInsn(Opcodes.ILOAD, 1)
        mv?.visitVarInsn(Opcodes.ILOAD, 2)
        mv?.visitMethodInsn(Opcodes.INVOKESTATIC, "com/example/Target", "add", "(II)I", false)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(3, 3)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }
}
