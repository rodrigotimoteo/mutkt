package com.github.rodrigotimoteo.mutkt.asm.filters

import com.github.rodrigotimoteo.mutkt.asm.finallyClasses
import com.github.rodrigotimoteo.mutkt.asm.parse
import com.github.rodrigotimoteo.mutkt.asm.toBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.ASM9
import org.objectweb.asm.Opcodes.ASTORE
import org.objectweb.asm.Opcodes.ATHROW
import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.Opcodes.GOTO
import org.objectweb.asm.Opcodes.ICONST_1
import org.objectweb.asm.Opcodes.IFNE
import org.objectweb.asm.Opcodes.ILOAD
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.Opcodes.NOP
import org.objectweb.asm.Opcodes.RETURN
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.VarInsnNode

class InlinedFinallyFilterTest {
    private fun noise(method: String) = InlinedFinallyFilter.noiseIn(finallyClasses, "SourceKt", method)

    private fun copies(
        count: Int,
        vararg body: String,
    ) = List(count) { body.toList() }.flatten()

    private val update = arrayOf("GETSTATIC", "BIPUSH", "IADD", "PUTSTATIC")
    private val bigUpdate = arrayOf("GETSTATIC", "SIPUSH", "IADD", "PUTSTATIC")

    @Test
    fun `a finally on return, normal and exception paths is one site, kept in the first copy`() {
        assertEquals(copies(2, *update), noise("tf"))
    }

    @Test
    fun `break and continue exits are copies too`() {
        assertEquals(copies(3, *bigUpdate), noise("loopTf"))
    }

    @Test
    fun `try catch finally copies the body to the catch exit as well`() {
        assertEquals(copies(2, *bigUpdate), noise("tcf"))
    }

    @Test
    fun `nested finally bodies are grouped each on its own`() {
        assertEquals(copies(4, *bigUpdate), noise("nested"))
    }

    @Test
    fun `a finally with control flow and jumps to its end is matched`() {
        assertEquals(
            copies(2, "ILOAD", "ICONST_3", "IF_ICMPLE", "GETSTATIC", "SIPUSH", "IADD", "PUTSTATIC"),
            noise("finIf"),
        )
    }

    @Test
    fun `copies that use other local slots are matched`() {
        assertEquals(
            copies(2, "ILOAD", "SIPUSH", "IADD", "ISTORE", "GETSTATIC", "ILOAD", "IADD", "PUTSTATIC"),
            noise("finLocal"),
        )
    }

    @Test
    fun `an inlined lambda in a finally is matched`() {
        val twins = noise("finLambda")

        assertEquals(86, twins.size)
        assertEquals(twins.take(43), twins.drop(43))
    }

    @Test
    fun `use closeFinally and a synchronized monitorexit are copies`() {
        assertEquals(listOf("ALOAD", "ALOAD", "INVOKESTATIC CloseableKt.closeFinally"), noise("closing"))
        assertEquals(listOf("ALOAD", "MONITOREXIT"), noise("sync"))
    }

    @Test
    fun `a finally that returns is not grouped, nor is code without a finally`() {
        assertEquals(emptyList<String>(), noise("finRet"))
        assertEquals(emptyList<String>(), noise("same"))
    }

    @Test
    fun `the same statement written in the try body is user code`() {
        assertEquals(copies(1, *bigUpdate), noise("lookalike"))
    }

    @Test
    fun `a copy that reads another variable is not a copy`() {
        assertEquals(copies(2, "GETSTATIC", "ILOAD", "IADD", "PUTSTATIC"), noise("reads"))

        val node = parse(finallyClasses.getValue("SourceKt"))
        val method = node.methods.single { it.name == "reads" }
        // The first copy after the try body reads a, like the template; make it read b.
        val read =
            method.instructions.filterIsInstance<VarInsnNode>().first {
                it.opcode == ILOAD &&
                    it.`var` == 0 &&
                    it.previous.opcode == GETSTATIC
            }
        read.`var` = 1

        assertEquals(
            emptyList<String>(),
            InlinedFinallyFilter.noiseIn(mapOf("SourceKt" to node.toBytes()), "SourceKt", "reads"),
        )
    }

    // try { work() } finally { ICONST_1 }: the copy on the normal path, then [afterCopy], then the handler's copy.
    private fun shape(
        beforeTry: List<AbstractInsnNode> = emptyList(),
        afterCopy: (copy: LabelNode) -> List<AbstractInsnNode> = { emptyList() },
        blocks: (copy: LabelNode) -> List<TryCatchBlockNode> = { emptyList() },
    ): MethodNode {
        val (tryStart, copy, handler) = List(3) { LabelNode() }
        val done = LabelNode()
        return MethodNode(ASM9, 0, "m", "()V", null, null).apply {
            beforeTry.forEach(instructions::add)
            instructions.add(tryStart)
            instructions.add(MethodInsnNode(INVOKESTATIC, "T", "work", "()V", false))
            instructions.add(copy)
            instructions.add(InsnNode(ICONST_1))
            afterCopy(copy).forEach(instructions::add)
            instructions.add(JumpInsnNode(GOTO, done))
            instructions.add(handler)
            listOf(
                VarInsnNode(ASTORE, 0),
                InsnNode(ICONST_1),
                VarInsnNode(ALOAD, 0),
                InsnNode(ATHROW),
            ).forEach(instructions::add)
            instructions.add(done)
            instructions.add(InsnNode(RETURN))
            tryCatchBlocks.add(TryCatchBlockNode(tryStart, copy, handler, null))
            tryCatchBlocks.addAll(blocks(copy))
        }
    }

    @Test
    fun `the copy after a try body is grouped with the handler copy`() {
        assertEquals(1, finallyTwins(shape()).size)
    }

    @Test
    fun `a copy that code after it jumps back into is not grouped`() {
        val method = shape(afterCopy = { copy -> listOf(JumpInsnNode(IFNE, copy)) })

        assertEquals(emptyMap<Any, Any>(), finallyTwins(method))
    }

    @Test
    fun `a copy that an exception handler outside the try region enters is not grouped`() {
        val range = Pair(LabelNode(), LabelNode())
        val method =
            shape(
                beforeTry = listOf(range.first, InsnNode(NOP), range.second),
                blocks = { copy -> listOf(TryCatchBlockNode(range.first, range.second, copy, "java/lang/Exception")) },
            )

        assertEquals(emptyMap<Any, Any>(), finallyTwins(method))
    }

    @Test
    fun `bytecode that does not look like a compiler finally is left alone, not an error`() {
        val start = LabelNode()
        val method =
            MethodNode(ASM9, 0, "m", "()V", null, null).apply {
                instructions.add(InsnNode(RETURN))
                tryCatchBlocks.add(TryCatchBlockNode(start, LabelNode(), LabelNode(), null))
            }

        assertEquals(emptyMap<Any, Any>(), finallyTwins(method))
    }
}
