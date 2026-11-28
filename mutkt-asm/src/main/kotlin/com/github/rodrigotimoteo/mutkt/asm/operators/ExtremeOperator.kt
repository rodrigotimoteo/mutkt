package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.ClassInfo
import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.asm.filters.isContinuationReentry
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ACONST_NULL
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.IRETURN
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode

// What the new body returns. The site's description names it, so apply follows the choice sites made.
private enum class Default(
    val label: String,
) {
    NOTHING(""),
    FALSE(" false"),
    ZERO(" 0"),
    EMPTY_STRING(" \"\""),
    EMPTY(" empty"),
    THIS(" this"),
    NULL(" null"),
    ;

    val description get() = "body -> return$label"
}

/**
 * Replaces a whole method body with a return of a default value (Descartes' extreme mutation): nothing for `void`,
 * false, zero, an empty String, array or collection, `this` when an instance method returns its own class (a
 * builder), else null. One site per method; a survivor marks the method as pseudo-tested.
 */
internal object ExtremeOperator : Operator {
    override val id = OperatorId("extreme")

    // The site is the method's first line, so the mutant reports it and a coverage probe there marks entry. A
    // continuation's re-entry only resumes the suspend function, which has a site of its own.
    override fun sites(
        method: MethodNode,
        cls: ClassInfo,
    ): List<Site> {
        val default = defaultFor(method, cls)
        val anchor = method.instructions.firstOrNull { it is LineNumberNode } ?: method.instructions.first
        return if (anchor == null || isContinuationReentry(method, cls) || returnsDefaultAlready(method, default)) {
            emptyList()
        } else {
            listOf(Site(anchor, default.description))
        }
    }

    // Try blocks, frames and local variable entries point into the old body, so they go with it.
    override fun apply(
        method: MethodNode,
        site: Site,
    ) {
        val default = Default.entries.single { it.description == site.description }
        val line = (site.instruction as? LineNumberNode)?.line
        val start = LabelNode()
        method.instructions.clear()
        method.tryCatchBlocks?.clear()
        method.localVariables?.clear()
        method.visibleLocalVariableAnnotations = null
        method.invisibleLocalVariableAnnotations = null
        method.instructions.add(start)
        line?.let { method.instructions.add(LineNumberNode(it, start)) }
        method.instructions.add(body(method, default))
    }

    private fun defaultFor(
        method: MethodNode,
        cls: ClassInfo,
    ): Default {
        val type = Type.getReturnType(method.desc)
        val instance = method.access and ACC_STATIC == 0
        return when {
            type == Type.VOID_TYPE -> Default.NOTHING
            type == Type.BOOLEAN_TYPE -> Default.FALSE
            type.sort != Type.OBJECT && type.sort != Type.ARRAY -> Default.ZERO
            type.descriptor == "Ljava/lang/String;" -> Default.EMPTY_STRING
            hasEmptyValue(type.descriptor) -> Default.EMPTY
            instance && type.internalName == cls.internalName -> Default.THIS
            else -> Default.NULL
        }
    }

    private fun body(
        method: MethodNode,
        default: Default,
    ): InsnList {
        val type = Type.getReturnType(method.desc)
        return InsnList().apply {
            when (default) {
                Default.NOTHING -> Unit
                Default.FALSE, Default.ZERO -> add(InsnNode(defaultOf(type)))
                Default.EMPTY_STRING -> add(LdcInsnNode(""))
                Default.EMPTY -> add(emptyValue(type.descriptor))
                Default.THIS -> add(VarInsnNode(ALOAD, 0))
                Default.NULL -> add(InsnNode(ACONST_NULL))
            }
            add(InsnNode(type.getOpcode(IRETURN)))
        }
    }

    // A body that already returns the default (`fun f() {}`, `= 0`, `= emptyList()`, `= this`) gives an
    // equivalent mutant. Kotlin's own empty factories count as the java.util one the mutant would call.
    private fun returnsDefaultAlready(
        method: MethodNode,
        default: Default,
    ) = method.instructions.filter { it.opcode >= 0 }.map(::key) ==
        body(method, default).filter { it.opcode >= 0 }.map(::key)

    private fun key(insn: AbstractInsnNode): String =
        when (insn) {
            is MethodInsnNode -> if (insn.isEmptyFactoryCall()) "empty" else "${insn.owner}.${insn.name}${insn.desc}"
            is LdcInsnNode -> "ldc ${insn.cst}"
            is TypeInsnNode -> "${insn.opcode} ${insn.desc}"
            is IntInsnNode -> "${insn.opcode} ${insn.operand}"
            is VarInsnNode -> "${insn.opcode} ${insn.`var`}"
            else -> "${insn.opcode}"
        }
}
