package com.github.rodrigotimoteo.mutation.mutator

import org.junit.jupiter.api.Test
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomMutatorRegistryTest {
    @Test
    fun `getMutators on empty registry returns empty list`() {
        val registry = CustomMutatorRegistry()
        assertEquals(0, registry.getMutators().size)
    }

    @Test
    fun `register adds a mutator to the registry`() {
        val registry = CustomMutatorRegistry()
        val mutator = FakeCustomMutator("test1")
        registry.register(mutator)
        assertEquals(1, registry.getMutators().size)
        assertEquals(mutator, registry.getMutator("test1"))
    }

    @Test
    fun `register adds multiple mutators`() {
        val registry = CustomMutatorRegistry()
        registry.register(FakeCustomMutator("test1"))
        registry.register(FakeCustomMutator("test2"))
        registry.register(FakeCustomMutator("test3"))
        assertEquals(3, registry.getMutators().size)
    }

    @Test
    fun `getMutator returns null for unknown name`() {
        val registry = CustomMutatorRegistry()
        registry.register(FakeCustomMutator("test1"))
        assertNull(registry.getMutator("unknown"))
    }

    @Test
    fun `getMutator returns null on empty registry`() {
        val registry = CustomMutatorRegistry()
        assertNull(registry.getMutator("anything"))
    }

    @Test
    fun `getMutators returns a copy not the internal list`() {
        val registry = CustomMutatorRegistry()
        registry.register(FakeCustomMutator("test1"))
        val list1 = registry.getMutators()
        val list2 = registry.getMutators()
        assertEquals(list1.size, list2.size)
        assertTrue(list1 !== list2, "getMutators should return a copy")
    }

    @Test
    fun `loadFromServiceLoader returns a non-null registry`() {
        val registry = CustomMutatorRegistry().loadFromServiceLoader()
        assertNotNull(registry)
    }

    @Test
    fun `loadFromServiceLoader returns a CustomMutatorRegistry instance`() {
        val registry = CustomMutatorRegistry().loadFromServiceLoader()
        assertNotNull(registry)
    }

    @Test
    fun `getMutator finds mutator by exact name`() {
        val registry = CustomMutatorRegistry()
        registry.register(FakeCustomMutator("foo"))
        registry.register(FakeCustomMutator("bar"))
        assertNotNull(registry.getMutator("foo"))
        assertNotNull(registry.getMutator("bar"))
        assertEquals("foo", registry.getMutator("foo")?.name)
        assertEquals("bar", registry.getMutator("bar")?.name)
    }

    @Test
    fun `getMutator is case-sensitive`() {
        val registry = CustomMutatorRegistry()
        registry.register(FakeCustomMutator("FOO"))
        assertNull(registry.getMutator("foo"))
    }
}

class FakeCustomMutator(override val name: String) : CustomMutator {
    override val description: String = "fake"

    override fun canMutate(methodNode: org.objectweb.asm.tree.MethodNode): Boolean = false

    override fun generateMutations(methodNode: org.objectweb.asm.tree.MethodNode): List<MutationInfo> = emptyList()

    override fun applyMutation(
        methodNode: org.objectweb.asm.tree.MethodNode,
        mutation: MutationInfo,
    ): org.objectweb.asm.tree.MethodNode = methodNode
}

class SealedClassWhenMutatorTest {
    @Test
    fun `isSealedClass returns true when metadata has isSealed=true`() {
        val metadata = mapOf("isSealed" to "true")
        assertTrue(SealedClassWhenMutator.isSealedClass(metadata))
    }

    @Test
    fun `isSealedClass returns false when metadata has isSealed=false`() {
        val metadata = mapOf("isSealed" to "false")
        assertFalse(SealedClassWhenMutator.isSealedClass(metadata))
    }

    @Test
    fun `isSealedClass returns false when metadata has no isSealed`() {
        val metadata = mapOf("other" to "true")
        assertFalse(SealedClassWhenMutator.isSealedClass(metadata))
    }

    @Test
    fun `isSealedClass returns false on empty metadata`() {
        assertFalse(SealedClassWhenMutator.isSealedClass(emptyMap()))
    }

    @Test
    fun `findWhenExpressions returns empty for no instructions`() {
        val result = SealedClassWhenMutator.findWhenExpressions(emptyList())
        assertEquals(0, result.size)
    }

    @Test
    fun `findWhenExpressions returns TABLESWITCH instruction`() {
        val label = Label()
        val info = InstructionInfo(Opcodes.TABLESWITCH, 10, label)
        val result = SealedClassWhenMutator.findWhenExpressions(listOf(info))
        assertEquals(1, result.size)
        assertEquals(Opcodes.TABLESWITCH, result.first().opcode)
    }

    @Test
    fun `findWhenExpressions returns LOOKUPSWITCH instruction`() {
        val label = Label()
        val info = InstructionInfo(Opcodes.LOOKUPSWITCH, 10, label)
        val result = SealedClassWhenMutator.findWhenExpressions(listOf(info))
        assertEquals(1, result.size)
        assertEquals(Opcodes.LOOKUPSWITCH, result.first().opcode)
    }

    @Test
    fun `findWhenExpressions filters out non-switch instructions`() {
        val instructions =
            listOf(
                InstructionInfo(Opcodes.ICONST_0, 1, Label()),
                InstructionInfo(Opcodes.TABLESWITCH, 2, Label()),
                InstructionInfo(Opcodes.IRETURN, 3, Label()),
                InstructionInfo(Opcodes.LOOKUPSWITCH, 4, Label()),
                InstructionInfo(Opcodes.IFNE, 5, Label()),
            )
        val result = SealedClassWhenMutator.findWhenExpressions(instructions)
        assertEquals(2, result.size)
    }

    @Test
    fun `findWhenExpressions returns empty for non-switch instructions only`() {
        val instructions =
            listOf(
                InstructionInfo(Opcodes.ICONST_0, 1, Label()),
                InstructionInfo(Opcodes.IRETURN, 2, Label()),
            )
        val result = SealedClassWhenMutator.findWhenExpressions(instructions)
        assertEquals(0, result.size)
    }

    @Test
    fun `generateMutations creates 2 mutations per branch (remove and wrong-return)`() {
        val whenInstr = InstructionInfo(Opcodes.TABLESWITCH, 10, Label())
        val mutations =
            SealedClassWhenMutator.generateMutations(
                className = "com.Foo",
                methodName = "handle",
                methodDescriptor = "()V",
                whenInstruction = whenInstr,
                branchCount = 3,
            )
        assertEquals(6, mutations.size)
    }

    @Test
    fun `generateMutations creates 0 mutations for branchCount=0`() {
        val whenInstr = InstructionInfo(Opcodes.TABLESWITCH, 10, Label())
        val mutations =
            SealedClassWhenMutator.generateMutations(
                className = "com.Foo",
                methodName = "handle",
                methodDescriptor = "()V",
                whenInstruction = whenInstr,
                branchCount = 0,
            )
        assertEquals(0, mutations.size)
    }

    @Test
    fun `generateMutations for 1 branch creates 2 mutations`() {
        val whenInstr = InstructionInfo(Opcodes.TABLESWITCH, 10, Label())
        val mutations =
            SealedClassWhenMutator.generateMutations(
                className = "com.Foo",
                methodName = "handle",
                methodDescriptor = "()V",
                whenInstruction = whenInstr,
                branchCount = 1,
            )
        assertEquals(2, mutations.size)
    }

    @Test
    fun `generateMutations metadata includes mutationType`() {
        val whenInstr = InstructionInfo(Opcodes.TABLESWITCH, 10, Label())
        val mutations =
            SealedClassWhenMutator.generateMutations(
                className = "com.Foo",
                methodName = "handle",
                methodDescriptor = "()V",
                whenInstruction = whenInstr,
                branchCount = 2,
            )
        val removeMutations = mutations.filter { it.metadata["mutationType"] == "REMOVE_BRANCH" }
        val wrongReturnMutations = mutations.filter { it.metadata["mutationType"] == "WRONG_RETURN" }
        assertEquals(2, removeMutations.size)
        assertEquals(2, wrongReturnMutations.size)
    }

    @Test
    fun `generateMutations metadata includes branchIndex`() {
        val whenInstr = InstructionInfo(Opcodes.TABLESWITCH, 10, Label())
        val mutations =
            SealedClassWhenMutator.generateMutations(
                className = "com.Foo",
                methodName = "handle",
                methodDescriptor = "()V",
                whenInstruction = whenInstr,
                branchCount = 3,
            )
        val indices = mutations.mapNotNull { it.metadata["branchIndex"]?.toInt() }
        assertEquals(6, indices.size)
        assertEquals(listOf(0, 0, 1, 1, 2, 2), indices.sorted())
    }

    @Test
    fun `generateMutations uses SEALED_WHEN operator`() {
        val whenInstr = InstructionInfo(Opcodes.TABLESWITCH, 10, Label())
        val mutations =
            SealedClassWhenMutator.generateMutations(
                className = "com.Foo",
                methodName = "handle",
                methodDescriptor = "()V",
                whenInstruction = whenInstr,
                branchCount = 2,
            )
        assertTrue(mutations.all { it.operator == MutationOperator.SEALED_WHEN })
    }

    @Test
    fun `generateMutations preserves className methodName methodDescriptor`() {
        val whenInstr = InstructionInfo(Opcodes.TABLESWITCH, 10, Label())
        val mutations =
            SealedClassWhenMutator.generateMutations(
                className = "com.example.Foo",
                methodName = "bar",
                methodDescriptor = "(I)V",
                whenInstruction = whenInstr,
                branchCount = 1,
            )
        mutations.forEach {
            assertEquals("com.example.Foo", it.className)
            assertEquals("bar", it.methodName)
            assertEquals("(I)V", it.methodDescriptor)
        }
    }

    @Test
    fun `generateMutations uses whenInstruction lineNumber`() {
        val whenInstr = InstructionInfo(Opcodes.TABLESWITCH, 42, Label())
        val mutations =
            SealedClassWhenMutator.generateMutations(
                className = "com.Foo",
                methodName = "bar",
                methodDescriptor = "()V",
                whenInstruction = whenInstr,
                branchCount = 2,
            )
        mutations.forEach { assertEquals(42, it.lineNumber) }
    }
}

class DataClassCopyMutatorTest {
    private fun makeMethod(
        name: String,
        descriptor: String,
        access: Int = Opcodes.ACC_PUBLIC,
    ) = MethodInfo(name = name, descriptor = descriptor, access = access)

    @Test
    fun `isDataClass returns true for class with all required methods`() {
        val methods =
            listOf(
                makeMethod("copy", "()Lcom/example/Foo;"),
                makeMethod("component1", "()I"),
                makeMethod("toString", "()Ljava/lang/String;"),
                makeMethod("hashCode", "()I"),
                makeMethod("equals", "(Ljava/lang/Object;)Z"),
            )
        assertTrue(DataClassCopyMutator.isDataClass(methods))
    }

    @Test
    fun `isDataClass returns false without copy method`() {
        val methods =
            listOf(
                makeMethod("component1", "()I"),
                makeMethod("toString", "()Ljava/lang/String;"),
                makeMethod("hashCode", "()I"),
                makeMethod("equals", "(Ljava/lang/Object;)Z"),
            )
        assertFalse(DataClassCopyMutator.isDataClass(methods))
    }

    @Test
    fun `isDataClass returns false without componentN methods`() {
        val methods =
            listOf(
                makeMethod("copy", "()Lcom/example/Foo;"),
                makeMethod("toString", "()Ljava/lang/String;"),
                makeMethod("hashCode", "()I"),
                makeMethod("equals", "(Ljava/lang/Object;)Z"),
            )
        assertFalse(DataClassCopyMutator.isDataClass(methods))
    }

    @Test
    fun `isDataClass returns false without toString`() {
        val methods =
            listOf(
                makeMethod("copy", "()Lcom/example/Foo;"),
                makeMethod("component1", "()I"),
                makeMethod("hashCode", "()I"),
                makeMethod("equals", "(Ljava/lang/Object;)Z"),
            )
        assertFalse(DataClassCopyMutator.isDataClass(methods))
    }

    @Test
    fun `isDataClass returns false on empty methods list`() {
        assertFalse(DataClassCopyMutator.isDataClass(emptyList()))
    }

    @Test
    fun `findCopyMethods returns the copy method`() {
        val methods =
            listOf(
                makeMethod("copy", "()Lcom/example/Foo;"),
                makeMethod("toString", "()Ljava/lang/String;"),
            )
        val result = DataClassCopyMutator.findCopyMethods(methods)
        assertEquals(1, result.size)
        assertEquals("copy", result.first().name)
    }

    @Test
    fun `findCopyMethods excludes synthetic copy methods`() {
        val methods =
            listOf(
                makeMethod("copy", "()Lcom/example/Foo;", access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNTHETIC),
                makeMethod("copy", "(I)Lcom/example/Foo;"),
            )
        val result = DataClassCopyMutator.findCopyMethods(methods)
        assertEquals(1, result.size)
        assertFalse(result.first().isSynthetic)
    }

    @Test
    fun `findCopyMethods returns empty if no copy method`() {
        val methods = listOf(makeMethod("toString", "()Ljava/lang/String;"))
        val result = DataClassCopyMutator.findCopyMethods(methods)
        assertEquals(0, result.size)
    }

    @Test
    fun `findCopyMethods returns empty for empty methods`() {
        val result = DataClassCopyMutator.findCopyMethods(emptyList())
        assertEquals(0, result.size)
    }

    @Test
    fun `generateMutations creates one mutation per parameter`() {
        val method = makeMethod("copy", "(Ljava/lang/String;I)Lcom/example/Foo;")
        val mutations =
            DataClassCopyMutator.generateMutations(
                className = "com.example.Foo",
                method = method,
                lineNumber = 10,
            )
        // 2 parameters = 2 mutations
        assertEquals(2, mutations.size)
    }

    @Test
    fun `generateMutations creates 0 mutations for no-arg copy`() {
        val method = makeMethod("copy", "()Lcom/example/Foo;")
        val mutations =
            DataClassCopyMutator.generateMutations(
                className = "com.example.Foo",
                method = method,
                lineNumber = 10,
            )
        assertEquals(0, mutations.size)
    }

    @Test
    fun `generateMutations uses DATA_CLASS_COPY operator`() {
        val method = makeMethod("copy", "(I)Lcom/example/Foo;")
        val mutations =
            DataClassCopyMutator.generateMutations(
                className = "com.example.Foo",
                method = method,
                lineNumber = 10,
            )
        assertTrue(mutations.all { it.operator == MutationOperator.DATA_CLASS_COPY })
    }

    @Test
    fun `generateMutations metadata includes parameterIndex`() {
        val method = makeMethod("copy", "(II)Lcom/example/Foo;")
        val mutations =
            DataClassCopyMutator.generateMutations(
                className = "com.example.Foo",
                method = method,
                lineNumber = 10,
            )
        val indices = mutations.mapNotNull { it.metadata["parameterIndex"]?.toInt() }
        assertEquals(listOf(0, 1), indices.sorted())
    }

    @Test
    fun `generateMutations metadata includes defaultValue`() {
        val method = makeMethod("copy", "(IZ)Lcom/example/Foo;")
        val mutations =
            DataClassCopyMutator.generateMutations(
                className = "com.example.Foo",
                method = method,
                lineNumber = 10,
            )
        val defaults = mutations.mapNotNull { it.metadata["defaultValue"] }
        // Int default = 0, Boolean default = false
        assertTrue(defaults.contains("0"))
        assertTrue(defaults.contains("false"))
    }
}

class NullSafetyMutatorTest {
    @Test
    fun `isSafeCall returns true for ALOAD IFNULL INVOKEVIRTUAL pattern`() {
        val instructions =
            listOf(
                InstructionInfo(Opcodes.ALOAD, 1, Label()),
                InstructionInfo(Opcodes.IFNULL, 1, Label()),
                InstructionInfo(Opcodes.INVOKEVIRTUAL, 1, Label()),
            )
        assertTrue(NullSafetyMutator.isSafeCall(instructions))
    }

    @Test
    fun `isSafeCall returns false for plain ALOAD INVOKEVIRTUAL`() {
        val instructions =
            listOf(
                InstructionInfo(Opcodes.ALOAD, 1, Label()),
                InstructionInfo(Opcodes.INVOKEVIRTUAL, 1, Label()),
            )
        assertFalse(NullSafetyMutator.isSafeCall(instructions))
    }

    @Test
    fun `isSafeCall returns false for empty instructions`() {
        assertFalse(NullSafetyMutator.isSafeCall(emptyList()))
    }

    @Test
    fun `isSafeCall returns false for IFNULL without INVOKEVIRTUAL within 5 instructions`() {
        val instructions =
            listOf(
                InstructionInfo(Opcodes.IFNULL, 1, Label()),
                InstructionInfo(Opcodes.ICONST_0, 1, Label()),
                InstructionInfo(Opcodes.ICONST_0, 1, Label()),
                InstructionInfo(Opcodes.ICONST_0, 1, Label()),
                InstructionInfo(Opcodes.ICONST_0, 1, Label()),
                InstructionInfo(Opcodes.ICONST_0, 1, Label()),
            )
        assertFalse(NullSafetyMutator.isSafeCall(instructions))
    }

    @Test
    fun `isNotNullAssertion returns true for DUP IFNULL ATHROW pattern`() {
        val instructions =
            listOf(
                InstructionInfo(Opcodes.ALOAD, 1, Label()),
                InstructionInfo(Opcodes.DUP, 1, Label()),
                InstructionInfo(Opcodes.IFNULL, 1, Label()),
                InstructionInfo(Opcodes.ATHROW, 1, Label()),
            )
        assertTrue(NullSafetyMutator.isNotNullAssertion(instructions))
    }

    @Test
    fun `isNotNullAssertion returns false for DUP without IFNULL`() {
        val instructions =
            listOf(
                InstructionInfo(Opcodes.DUP, 1, Label()),
                InstructionInfo(Opcodes.ICONST_0, 1, Label()),
            )
        assertFalse(NullSafetyMutator.isNotNullAssertion(instructions))
    }

    @Test
    fun `isNotNullAssertion returns false for IFNULL without ATHROW`() {
        val instructions =
            listOf(
                InstructionInfo(Opcodes.DUP, 1, Label()),
                InstructionInfo(Opcodes.IFNULL, 1, Label()),
                InstructionInfo(Opcodes.ICONST_0, 1, Label()),
            )
        assertFalse(NullSafetyMutator.isNotNullAssertion(instructions))
    }

    @Test
    fun `isNotNullAssertion returns false for empty instructions`() {
        assertFalse(NullSafetyMutator.isNotNullAssertion(emptyList()))
    }

    @Test
    fun `isNotNullAssertion returns false for DUP without IFNULL in next 3 instructions`() {
        val instructions =
            listOf(
                InstructionInfo(Opcodes.DUP, 1, Label()),
                InstructionInfo(Opcodes.ICONST_0, 1, Label()),
                InstructionInfo(Opcodes.ICONST_0, 1, Label()),
                InstructionInfo(Opcodes.ICONST_0, 1, Label()),
            )
        assertFalse(NullSafetyMutator.isNotNullAssertion(instructions))
    }

    @Test
    fun `isElvisOperator returns true for DUP IFNONNULL POP pattern`() {
        val instructions =
            listOf(
                InstructionInfo(Opcodes.ALOAD, 1, Label()),
                InstructionInfo(Opcodes.DUP, 1, Label()),
                InstructionInfo(Opcodes.IFNONNULL, 1, Label()),
                InstructionInfo(Opcodes.POP, 1, Label()),
            )
        assertTrue(NullSafetyMutator.isElvisOperator(instructions))
    }

    @Test
    fun `isElvisOperator returns false for DUP without IFNONNULL`() {
        val instructions =
            listOf(
                InstructionInfo(Opcodes.DUP, 1, Label()),
                InstructionInfo(Opcodes.ICONST_0, 1, Label()),
            )
        assertFalse(NullSafetyMutator.isElvisOperator(instructions))
    }

    @Test
    fun `isElvisOperator returns false for IFNONNULL without POP`() {
        val instructions =
            listOf(
                InstructionInfo(Opcodes.DUP, 1, Label()),
                InstructionInfo(Opcodes.IFNONNULL, 1, Label()),
                InstructionInfo(Opcodes.ICONST_0, 1, Label()),
            )
        assertFalse(NullSafetyMutator.isElvisOperator(instructions))
    }

    @Test
    fun `isElvisOperator returns false for empty instructions`() {
        assertFalse(NullSafetyMutator.isElvisOperator(emptyList()))
    }

    @Test
    fun `generateMutations for SAFE_CALL creates 2 mutations`() {
        val instr = InstructionInfo(Opcodes.IFNULL, 10, Label())
        val mutations =
            NullSafetyMutator.generateMutations(
                className = "com.Foo",
                methodName = "bar",
                methodDescriptor = "()V",
                instruction = instr,
                type = NullSafetyType.SAFE_CALL,
            )
        assertEquals(2, mutations.size)
    }

    @Test
    fun `generateMutations for NOT_NULL_ASSERTION creates 1 mutation`() {
        val instr = InstructionInfo(Opcodes.IFNULL, 10, Label())
        val mutations =
            NullSafetyMutator.generateMutations(
                className = "com.Foo",
                methodName = "bar",
                methodDescriptor = "()V",
                instruction = instr,
                type = NullSafetyType.NOT_NULL_ASSERTION,
            )
        assertEquals(1, mutations.size)
    }

    @Test
    fun `generateMutations for ELVIS creates 2 mutations`() {
        val instr = InstructionInfo(Opcodes.IFNONNULL, 10, Label())
        val mutations =
            NullSafetyMutator.generateMutations(
                className = "com.Foo",
                methodName = "bar",
                methodDescriptor = "()V",
                instruction = instr,
                type = NullSafetyType.ELVIS,
            )
        assertEquals(2, mutations.size)
    }

    @Test
    fun `generateMutations uses NULL_SAFETY operator`() {
        val instr = InstructionInfo(Opcodes.IFNULL, 10, Label())
        val mutations =
            NullSafetyMutator.generateMutations(
                className = "com.Foo",
                methodName = "bar",
                methodDescriptor = "()V",
                instruction = instr,
                type = NullSafetyType.SAFE_CALL,
            )
        assertTrue(mutations.all { it.operator == MutationOperator.NULL_SAFETY })
    }

    @Test
    fun `generateMutations metadata for SAFE_CALL includes mutationType`() {
        val instr = InstructionInfo(Opcodes.IFNULL, 10, Label())
        val mutations =
            NullSafetyMutator.generateMutations(
                className = "com.Foo",
                methodName = "bar",
                methodDescriptor = "()V",
                instruction = instr,
                type = NullSafetyType.SAFE_CALL,
            )
        val types = mutations.mapNotNull { it.metadata["mutationType"] }
        assertTrue(types.contains("REMOVE_NULL_CHECK"))
        assertTrue(types.contains("ALWAYS_NULL"))
    }
}

class CoroutineMutatorTest {
    @Test
    fun `isSuspendFunction returns true when ACC_SYNCHRONIZED is set`() {
        val access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNCHRONIZED
        assertTrue(CoroutineMutator.isSuspendFunction(access))
    }

    @Test
    fun `isSuspendFunction returns false when ACC_SYNCHRONIZED is not set`() {
        val access = Opcodes.ACC_PUBLIC
        assertFalse(CoroutineMutator.isSuspendFunction(access))
    }

    @Test
    fun `isSuspendFunction returns false for private method`() {
        val access = Opcodes.ACC_PRIVATE
        assertFalse(CoroutineMutator.isSuspendFunction(access))
    }

    @Test
    fun `isSuspendFunction returns true for ACC_PRIVATE SYNCHRONIZED`() {
        val access = Opcodes.ACC_PRIVATE or Opcodes.ACC_SYNCHRONIZED
        assertTrue(CoroutineMutator.isSuspendFunction(access))
    }

    @Test
    fun `isCoroutineBuilder returns true for runBlocking`() {
        assertTrue(CoroutineMutator.isCoroutineBuilder("runBlocking"))
    }

    @Test
    fun `isCoroutineBuilder returns true for launch`() {
        assertTrue(CoroutineMutator.isCoroutineBuilder("launch"))
    }

    @Test
    fun `isCoroutineBuilder returns true for async`() {
        assertTrue(CoroutineMutator.isCoroutineBuilder("async"))
    }

    @Test
    fun `isCoroutineBuilder returns true for withContext`() {
        assertTrue(CoroutineMutator.isCoroutineBuilder("withContext"))
    }

    @Test
    fun `isCoroutineBuilder returns true for coroutineScope`() {
        assertTrue(CoroutineMutator.isCoroutineBuilder("coroutineScope"))
    }

    @Test
    fun `isCoroutineBuilder returns true for supervisorScope`() {
        assertTrue(CoroutineMutator.isCoroutineBuilder("supervisorScope"))
    }

    @Test
    fun `isCoroutineBuilder returns true for runTest`() {
        assertTrue(CoroutineMutator.isCoroutineBuilder("runTest"))
    }

    @Test
    fun `isCoroutineBuilder returns false for non-coroutine method`() {
        assertFalse(CoroutineMutator.isCoroutineBuilder("foo"))
    }

    @Test
    fun `isCoroutineBuilder returns false for main`() {
        assertFalse(CoroutineMutator.isCoroutineBuilder("main"))
    }

    @Test
    fun `isDispatcherLoad returns true when GETSTATIC present`() {
        val instructions =
            listOf(
                InstructionInfo(Opcodes.GETSTATIC, 1, Label()),
            )
        assertTrue(CoroutineMutator.isDispatcherLoad(instructions))
    }

    @Test
    fun `isDispatcherLoad returns false when no GETSTATIC`() {
        val instructions =
            listOf(
                InstructionInfo(Opcodes.ICONST_0, 1, Label()),
                InstructionInfo(Opcodes.IRETURN, 2, Label()),
            )
        assertFalse(CoroutineMutator.isDispatcherLoad(instructions))
    }

    @Test
    fun `isDispatcherLoad returns false on empty instructions`() {
        assertFalse(CoroutineMutator.isDispatcherLoad(emptyList()))
    }

    @Test
    fun `isDispatcherLoad returns true even with GETSTATIC in middle`() {
        val instructions =
            listOf(
                InstructionInfo(Opcodes.ICONST_0, 1, Label()),
                InstructionInfo(Opcodes.GETSTATIC, 2, Label()),
                InstructionInfo(Opcodes.ICONST_1, 3, Label()),
            )
        assertTrue(CoroutineMutator.isDispatcherLoad(instructions))
    }
}
