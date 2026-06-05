package com.github.rodrigotimoteo.mutation.mutator

import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoroutineMutatorExtendedTest {
    @Test
    fun `isSuspendFunction true for synchronized flag`() {
        val access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNCHRONIZED
        assertTrue(CoroutineMutator.isSuspendFunction(access))
    }

    @Test
    fun `isSuspendFunction false without synchronized flag`() {
        val access = Opcodes.ACC_PUBLIC
        assertFalse(CoroutineMutator.isSuspendFunction(access))
    }

    @Test
    fun `isSuspendFunction false for static synchronized`() {
        val access = Opcodes.ACC_STATIC or Opcodes.ACC_SYNCHRONIZED
        assertTrue(CoroutineMutator.isSuspendFunction(access), "Static + synchronized is also suspend-like")
    }

    @Test
    fun `isSuspendFunction true for private synchronized`() {
        val access = Opcodes.ACC_PRIVATE or Opcodes.ACC_SYNCHRONIZED
        assertTrue(CoroutineMutator.isSuspendFunction(access))
    }

    @Test
    fun `isCoroutineBuilder true for runBlocking`() {
        assertTrue(CoroutineMutator.isCoroutineBuilder("runBlocking"))
    }

    @Test
    fun `isCoroutineBuilder true for launch`() {
        assertTrue(CoroutineMutator.isCoroutineBuilder("launch"))
    }

    @Test
    fun `isCoroutineBuilder true for async`() {
        assertTrue(CoroutineMutator.isCoroutineBuilder("async"))
    }

    @Test
    fun `isCoroutineBuilder true for withContext`() {
        assertTrue(CoroutineMutator.isCoroutineBuilder("withContext"))
    }

    @Test
    fun `isCoroutineBuilder true for coroutineScope`() {
        assertTrue(CoroutineMutator.isCoroutineBuilder("coroutineScope"))
    }

    @Test
    fun `isCoroutineBuilder true for supervisorScope`() {
        assertTrue(CoroutineMutator.isCoroutineBuilder("supervisorScope"))
    }

    @Test
    fun `isCoroutineBuilder true for runTest`() {
        assertTrue(CoroutineMutator.isCoroutineBuilder("runTest"))
    }

    @Test
    fun `isCoroutineBuilder false for unknown names`() {
        assertFalse(CoroutineMutator.isCoroutineBuilder("foo"))
        assertFalse(CoroutineMutator.isCoroutineBuilder("bar"))
        assertFalse(CoroutineMutator.isCoroutineBuilder(""))
    }

    @Test
    fun `isCoroutineBuilder case-sensitive`() {
        assertFalse(CoroutineMutator.isCoroutineBuilder("RUNBLOCKING"))
        assertFalse(CoroutineMutator.isCoroutineBuilder("Launch"))
    }

    @Test
    fun `isDispatcherLoad true for GETSTATIC instruction`() {
        val instructions =
            listOf(
                InstructionInfo(opcode = Opcodes.GETSTATIC, lineNumber = 1),
            )
        assertTrue(CoroutineMutator.isDispatcherLoad(instructions))
    }

    @Test
    fun `isDispatcherLoad false for empty instructions`() {
        assertFalse(CoroutineMutator.isDispatcherLoad(emptyList()))
    }

    @Test
    fun `isDispatcherLoad false for non-GETSTATIC instructions`() {
        val instructions =
            listOf(
                InstructionInfo(opcode = Opcodes.ICONST_0, lineNumber = 1),
                InstructionInfo(opcode = Opcodes.IRETURN, lineNumber = 2),
            )
        assertFalse(CoroutineMutator.isDispatcherLoad(instructions))
    }

    @Test
    fun `isDispatcherLoad true for any GETSTATIC in suspend context`() {
        // Per current implementation, any GETSTATIC in suspend context is a dispatcher
        val instructions =
            listOf(
                InstructionInfo(opcode = Opcodes.GETSTATIC, lineNumber = 1),
            )
        assertTrue(CoroutineMutator.isDispatcherLoad(instructions))
    }

    @Test
    fun `generateMutations for suspend function returns 2 mutations`() {
        val method =
            MethodInfo(
                name = "fetch",
                descriptor = "()Ljava/lang/String;",
                access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNCHRONIZED,
            )
        val mutations = CoroutineMutator.generateMutations("com.Foo", method, 10)
        assertEquals(2, mutations.size)
        assertTrue(mutations.all { it.operator == MutationOperator.COROUTINE })
    }

    @Test
    fun `generateMutations for non-suspend returns 0 mutations`() {
        val method =
            MethodInfo(
                name = "regular",
                descriptor = "()I",
                access = Opcodes.ACC_PUBLIC,
            )
        val mutations = CoroutineMutator.generateMutations("com.Foo", method, 10)
        assertEquals(0, mutations.size)
    }

    @Test
    fun `generateMutations for coroutine builder returns 1 mutation`() {
        val method =
            MethodInfo(
                name = "runBlocking",
                descriptor = "()Ljava/lang/Object;",
                access = Opcodes.ACC_PUBLIC,
            )
        val mutations = CoroutineMutator.generateMutations("com.Foo", method, 10)
        assertEquals(1, mutations.size)
        assertEquals(MutationOperator.COROUTINE, mutations[0].operator)
    }

    @Test
    fun `generateMutations for suspend + builder returns 3 mutations`() {
        val method =
            MethodInfo(
                name = "runBlocking",
                descriptor = "()Ljava/lang/Object;",
                access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNCHRONIZED,
            )
        val mutations = CoroutineMutator.generateMutations("com.Foo", method, 10)
        assertEquals(3, mutations.size)
    }

    @Test
    fun `generateMutations SKIP_SUSPEND_BODY has metadata`() {
        val method =
            MethodInfo(
                name = "fetch",
                descriptor = "()Ljava/lang/String;",
                access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNCHRONIZED,
            )
        val mutations = CoroutineMutator.generateMutations("com.Foo", method, 10)
        val skip = mutations.first { it.metadata["mutationType"] == "SKIP_SUSPEND_BODY" }
        assertNotNull(skip)
        assertEquals("java.lang.String", skip.metadata["returnType"])
    }

    @Test
    fun `generateMutations THROW_CANCELLATION has metadata`() {
        val method =
            MethodInfo(
                name = "fetch",
                descriptor = "()V",
                access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNCHRONIZED,
            )
        val mutations = CoroutineMutator.generateMutations("com.Foo", method, 10)
        val throwMutation = mutations.first { it.metadata["mutationType"] == "THROW_CANCELLATION" }
        assertNotNull(throwMutation)
    }

    @Test
    fun `generateMutations SKIP_BUILDER_BODY has metadata`() {
        val method =
            MethodInfo(
                name = "launch",
                descriptor = "()V",
                access = Opcodes.ACC_PUBLIC,
            )
        val mutations = CoroutineMutator.generateMutations("com.Foo", method, 10)
        val skip = mutations.first { it.metadata["mutationType"] == "SKIP_BUILDER_BODY" }
        assertNotNull(skip)
    }

    @Test
    fun `generateMutations includes className methodName and lineNumber`() {
        val method =
            MethodInfo(
                name = "fetch",
                descriptor = "()V",
                access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNCHRONIZED,
            )
        val mutations = CoroutineMutator.generateMutations("com.example.Foo", method, 42)
        assertTrue(mutations.all { it.className == "com.example.Foo" })
        assertTrue(mutations.all { it.methodName == "fetch" })
        assertTrue(mutations.all { it.lineNumber == 42 })
    }
}

class DataClassCopyMutatorExtendedTest {
    @Test
    fun `isDataClass true for class with all required methods`() {
        val methods =
            listOf(
                MethodInfo("copy", "()LData;", Opcodes.ACC_PUBLIC),
                MethodInfo("component1", "()I", Opcodes.ACC_PUBLIC),
                MethodInfo("toString", "()Ljava/lang/String;", Opcodes.ACC_PUBLIC),
                MethodInfo("hashCode", "()I", Opcodes.ACC_PUBLIC),
                MethodInfo("equals", "(Ljava/lang/Object;)Z", Opcodes.ACC_PUBLIC),
            )
        assertTrue(DataClassCopyMutator.isDataClass(methods))
    }

    @Test
    fun `isDataClass false without copy`() {
        val methods =
            listOf(
                MethodInfo("component1", "()I", Opcodes.ACC_PUBLIC),
                MethodInfo("toString", "()Ljava/lang/String;", Opcodes.ACC_PUBLIC),
                MethodInfo("hashCode", "()I", Opcodes.ACC_PUBLIC),
                MethodInfo("equals", "(Ljava/lang/Object;)Z", Opcodes.ACC_PUBLIC),
            )
        assertFalse(DataClassCopyMutator.isDataClass(methods))
    }

    @Test
    fun `isDataClass false without componentN`() {
        val methods =
            listOf(
                MethodInfo("copy", "()LData;", Opcodes.ACC_PUBLIC),
                MethodInfo("toString", "()Ljava/lang/String;", Opcodes.ACC_PUBLIC),
                MethodInfo("hashCode", "()I", Opcodes.ACC_PUBLIC),
                MethodInfo("equals", "(Ljava/lang/Object;)Z", Opcodes.ACC_PUBLIC),
            )
        assertFalse(DataClassCopyMutator.isDataClass(methods))
    }

    @Test
    fun `isDataClass false without toString`() {
        val methods =
            listOf(
                MethodInfo("copy", "()LData;", Opcodes.ACC_PUBLIC),
                MethodInfo("component1", "()I", Opcodes.ACC_PUBLIC),
                MethodInfo("hashCode", "()I", Opcodes.ACC_PUBLIC),
                MethodInfo("equals", "(Ljava/lang/Object;)Z", Opcodes.ACC_PUBLIC),
            )
        assertFalse(DataClassCopyMutator.isDataClass(methods))
    }

    @Test
    fun `isDataClass false without hashCode`() {
        val methods =
            listOf(
                MethodInfo("copy", "()LData;", Opcodes.ACC_PUBLIC),
                MethodInfo("component1", "()I", Opcodes.ACC_PUBLIC),
                MethodInfo("toString", "()Ljava/lang/String;", Opcodes.ACC_PUBLIC),
                MethodInfo("equals", "(Ljava/lang/Object;)Z", Opcodes.ACC_PUBLIC),
            )
        assertFalse(DataClassCopyMutator.isDataClass(methods))
    }

    @Test
    fun `isDataClass false without equals`() {
        val methods =
            listOf(
                MethodInfo("copy", "()LData;", Opcodes.ACC_PUBLIC),
                MethodInfo("component1", "()I", Opcodes.ACC_PUBLIC),
                MethodInfo("toString", "()Ljava/lang/String;", Opcodes.ACC_PUBLIC),
                MethodInfo("hashCode", "()I", Opcodes.ACC_PUBLIC),
            )
        assertFalse(DataClassCopyMutator.isDataClass(methods))
    }

    @Test
    fun `isDataClass false for empty methods list`() {
        assertFalse(DataClassCopyMutator.isDataClass(emptyList()))
    }

    @Test
    fun `isDataClass true with multiple componentN methods`() {
        val methods =
            listOf(
                MethodInfo("copy", "()LData;", Opcodes.ACC_PUBLIC),
                MethodInfo("component1", "()I", Opcodes.ACC_PUBLIC),
                MethodInfo("component2", "()Ljava/lang/String;", Opcodes.ACC_PUBLIC),
                MethodInfo("component3", "()Z", Opcodes.ACC_PUBLIC),
                MethodInfo("toString", "()Ljava/lang/String;", Opcodes.ACC_PUBLIC),
                MethodInfo("hashCode", "()I", Opcodes.ACC_PUBLIC),
                MethodInfo("equals", "(Ljava/lang/Object;)Z", Opcodes.ACC_PUBLIC),
            )
        assertTrue(DataClassCopyMutator.isDataClass(methods))
    }

    @Test
    fun `findCopyMethods returns only non-synthetic copy methods`() {
        val methods =
            listOf(
                MethodInfo("copy", "()LData;", Opcodes.ACC_PUBLIC),
                MethodInfo("copy", "()LData;", Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNTHETIC),
                MethodInfo("other", "()V", Opcodes.ACC_PUBLIC),
            )
        val result = DataClassCopyMutator.findCopyMethods(methods)
        assertEquals(1, result.size)
        assertEquals("copy", result[0].name)
        assertFalse(result[0].isSynthetic)
    }

    @Test
    fun `findCopyMethods returns empty if no copy`() {
        val methods =
            listOf(
                MethodInfo("foo", "()V", Opcodes.ACC_PUBLIC),
            )
        val result = DataClassCopyMutator.findCopyMethods(methods)
        assertEquals(0, result.size)
    }

    @Test
    fun `findCopyMethods returns empty for synthetic-only copy`() {
        val methods =
            listOf(
                MethodInfo("copy", "()LData;", Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNTHETIC),
            )
        val result = DataClassCopyMutator.findCopyMethods(methods)
        assertEquals(0, result.size)
    }

    @Test
    fun `generateMutations creates one per parameter`() {
        val method =
            MethodInfo(
                name = "copy",
                descriptor = "(ILjava/lang/String;Z)LData;",
                access = Opcodes.ACC_PUBLIC,
            )
        val mutations = DataClassCopyMutator.generateMutations("com.Foo", method, 10)
        assertEquals(3, mutations.size)
        assertTrue(mutations.all { it.operator == MutationOperator.DATA_CLASS_COPY })
    }

    @Test
    fun `generateMutations for zero params returns empty`() {
        val method =
            MethodInfo(
                name = "copy",
                descriptor = "()LData;",
                access = Opcodes.ACC_PUBLIC,
            )
        val mutations = DataClassCopyMutator.generateMutations("com.Foo", method, 10)
        assertEquals(0, mutations.size)
    }

    @Test
    fun `generateMutations uses correct default for primitives`() {
        val method =
            MethodInfo(
                name = "copy",
                descriptor = "(I)LData;",
                access = Opcodes.ACC_PUBLIC,
            )
        val mutations = DataClassCopyMutator.generateMutations("com.Foo", method, 10)
        assertEquals("0", mutations[0].metadata["defaultValue"])
    }

    @Test
    fun `generateMutations uses null for object types`() {
        val method =
            MethodInfo(
                name = "copy",
                descriptor = "(Ljava/lang/Object;)LData;",
                access = Opcodes.ACC_PUBLIC,
            )
        val mutations = DataClassCopyMutator.generateMutations("com.Foo", method, 10)
        assertEquals("null", mutations[0].metadata["defaultValue"])
    }

    @Test
    fun `generateMutations uses empty string for String type`() {
        val method =
            MethodInfo(
                name = "copy",
                descriptor = "(Ljava/lang/String;)LData;",
                access = Opcodes.ACC_PUBLIC,
            )
        val mutations = DataClassCopyMutator.generateMutations("com.Foo", method, 10)
        assertEquals("\"\"", mutations[0].metadata["defaultValue"])
    }

    @Test
    fun `generateMutations uses empty array for array type`() {
        val method =
            MethodInfo(
                name = "copy",
                descriptor = "([I)LData;",
                access = Opcodes.ACC_PUBLIC,
            )
        val mutations = DataClassCopyMutator.generateMutations("com.Foo", method, 10)
        assertEquals("[]", mutations[0].metadata["defaultValue"])
    }

    @Test
    fun `MethodInfo isStatic true for static method`() {
        val method = MethodInfo("foo", "()V", Opcodes.ACC_STATIC)
        assertTrue(method.isStatic)
    }

    @Test
    fun `MethodInfo isStatic false for instance method`() {
        val method = MethodInfo("foo", "()V", Opcodes.ACC_PUBLIC)
        assertFalse(method.isStatic)
    }

    @Test
    fun `MethodInfo isSynthetic true for synthetic method`() {
        val method = MethodInfo("foo", "()V", Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNTHETIC)
        assertTrue(method.isSynthetic)
    }

    @Test
    fun `MethodInfo isBridge true for bridge method`() {
        val method = MethodInfo("foo", "()V", Opcodes.ACC_PUBLIC or Opcodes.ACC_BRIDGE)
        assertTrue(method.isBridge)
    }

    @Test
    fun `MethodInfo equality based on all fields`() {
        val a = MethodInfo("foo", "()V", Opcodes.ACC_PUBLIC)
        val b = MethodInfo("foo", "()V", Opcodes.ACC_PUBLIC)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
