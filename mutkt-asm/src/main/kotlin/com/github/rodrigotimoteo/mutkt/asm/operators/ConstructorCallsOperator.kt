package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Opcodes.ACONST_NULL
import org.objectweb.asm.Opcodes.ARETURN
import org.objectweb.asm.Opcodes.ATHROW
import org.objectweb.asm.Opcodes.DUP
import org.objectweb.asm.Opcodes.INVOKESPECIAL
import org.objectweb.asm.Opcodes.NEW
import org.objectweb.asm.Opcodes.POP
import org.objectweb.asm.Opcodes.POP2
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode

private const val STRING_BUILDER = "java/lang/StringBuilder"

// "new" and "dup" are the NEW and DUP that open the construction, "init" the INVOKESPECIAL that closes it.
private class Construction(
    val new: TypeInsnNode,
    val dup: InsnNode,
    val init: MethodInsnNode,
)

/**
 * Replaces a whole `new T(args)` expression (NEW, DUP, args, INVOKESPECIAL) with its args popped and null.
 * StringBuilder constructions, constructions directly thrown and constructions directly returned are skipped.
 */
internal object ConstructorCallsOperator : Operator {
    override val id = OperatorId("constructor-calls")

    override fun sites(method: MethodNode): List<Site> =
        constructions(method).map { Site(it.init, "new ${it.new.desc.replace('/', '.')} -> null") }

    // The args are already on the stack, so dropping NEW and DUP leaves them to be popped before the null.
    override fun apply(
        method: MethodNode,
        site: Site,
    ) {
        val found = constructions(method).single { it.init === site.instruction }
        val replacement =
            InsnList().apply {
                Type.getArgumentTypes(found.init.desc).reversed().forEach {
                    add(InsnNode(if (it.size == 2) POP2 else POP))
                }
                add(InsnNode(ACONST_NULL))
            }
        method.instructions.insertBefore(found.init, replacement)
        method.instructions.remove(found.init)
        method.instructions.remove(found.dup)
        method.instructions.remove(found.new)
    }

    // Constructions nest (NEW inside the args of another), so open NEWs sit on a stack and each <init> closes
    // the innermost one of its own type. An <init> with no open NEW is a super() or this() call.
    private fun constructions(method: MethodNode): List<Construction> {
        val open = ArrayDeque<TypeInsnNode>()
        return method.instructions
            .mapNotNull { insn ->
                when {
                    insn.opcode == NEW -> null.also { open.addLast(insn as TypeInsnNode) }
                    insn is MethodInsnNode && insn.opcode == INVOKESPECIAL && insn.name == "<init>" ->
                        open.lastOrNull()?.takeIf { it.desc == insn.owner }?.let {
                            open.removeLast()
                            dupAfter(it)?.let { dup -> Construction(it, dup, insn) }?.takeUnless(::isNoise)
                        }
                    else -> null
                }
            }
    }

    // String templates compile to StringBuilder constructions, and `throw X()` only swaps the intended exception for
    // an NPE: both are noise. A directly returned construction is what null-returns already mutates.
    private fun isNoise(c: Construction): Boolean =
        c.new.desc == STRING_BUILDER || nextReal(c.init)?.opcode.let { it == ATHROW || it == ARETURN }

    private fun nextReal(insn: AbstractInsnNode): AbstractInsnNode? =
        generateSequence(insn.next) { it.next }.firstOrNull { it.opcode >= 0 }

    // Kotlin and javac always emit DUP right after NEW; anything else is not the shape this operator understands.
    private fun dupAfter(new: TypeInsnNode): InsnNode? = (nextReal(new) as? InsnNode)?.takeIf { it.opcode == DUP }
}
