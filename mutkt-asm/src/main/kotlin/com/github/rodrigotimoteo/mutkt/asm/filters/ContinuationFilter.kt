package com.github.rodrigotimoteo.mutkt.asm.filters

import com.github.rodrigotimoteo.mutkt.asm.SiteFilter
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.GETFIELD
import org.objectweb.asm.Opcodes.IAND
import org.objectweb.asm.Opcodes.IFEQ
import org.objectweb.asm.Opcodes.INSTANCEOF
import org.objectweb.asm.Opcodes.IOR
import org.objectweb.asm.Opcodes.ISUB
import org.objectweb.asm.Opcodes.PUTFIELD
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode

/**
 * The continuation plumbing of a suspend function: its prologue (`$completion instanceof Owner$fn$1` on the
 * continuation parameter, then the `label & MIN_VALUE` test and the `label - MIN_VALUE` on a `label` field of
 * exactly that class) and the `invokeSuspend` of the continuation class (`label |= MIN_VALUE` on its own field,
 * then call the function again). A user's own `label` field, even in a class nested in the owner, never matches.
 * No debug info needed.
 */
internal object ContinuationFilter : SiteFilter {
    override fun noise(
        className: String,
        method: MethodNode,
    ): Set<AbstractInsnNode> =
        when {
            !isSuspendShape(method) -> emptySet()
            // Operators judge the re-entry call by the continuation's superclass, which a filter does not see.
            isTrampoline(className, method) -> method.instructions.filterNot { it is MethodInsnNode }.toSet()
            else -> prologue(className, method)
        }

    private fun prologue(
        className: String,
        method: MethodNode,
    ): Set<AbstractInsnNode> {
        val check = method.instructions.firstOrNull { isCompletionCheck(it, className, method) } ?: return emptySet()
        val continuation = (check as TypeInsnNode).desc
        return method.instructions
            .filter { insn ->
                val prev = realBefore(insn)
                when (insn.opcode) {
                    IFEQ -> prev === check || (prev?.opcode == IAND && isLabelMask(prev, continuation))
                    ISUB -> isLabelMask(insn, continuation)
                    else -> false
                }
            }.toSet()
    }

    private fun isCompletionCheck(
        check: AbstractInsnNode,
        className: String,
        method: MethodNode,
    ): Boolean {
        val load = realBefore(check) as? VarInsnNode
        return check is TypeInsnNode &&
            check.opcode == INSTANCEOF &&
            check.desc.startsWith("$className$") &&
            load?.opcode == ALOAD &&
            load.`var` == completionSlot(method) &&
            realAfter(check)?.opcode == IFEQ
    }

    // `GETFIELD owner.label; LDC MIN_VALUE` right before [op] (an IAND, ISUB or IOR).
    private fun isLabelMask(
        op: AbstractInsnNode,
        owner: String,
    ): Boolean {
        val constant = realBefore(op)
        val label = constant?.let(::realBefore) as? FieldInsnNode
        return constant is LdcInsnNode &&
            constant.cst == Int.MIN_VALUE &&
            label?.opcode == GETFIELD &&
            label.isLabelOf(owner)
    }

    private fun isTrampoline(
        className: String,
        method: MethodNode,
    ) = method.name + method.desc == INVOKE_SUSPEND &&
        method.instructions.any { set ->
            val or = realBefore(set)
            set is FieldInsnNode &&
                set.opcode == PUTFIELD &&
                set.isLabelOf(className) &&
                or?.opcode == IOR &&
                isLabelMask(or, className)
        }

    private fun FieldInsnNode.isLabelOf(owner: String) = name == "label" && desc == "I" && this.owner == owner

    // The continuation is the last parameter of a suspend function.
    private fun completionSlot(method: MethodNode): Int? {
        val params = Type.getArgumentTypes(method.desc)
        if (params.lastOrNull()?.descriptor != CONTINUATION) return null
        val first = if (method.access and ACC_STATIC != 0) 0 else 1
        return first + params.dropLast(1).sumOf { it.size }
    }
}
