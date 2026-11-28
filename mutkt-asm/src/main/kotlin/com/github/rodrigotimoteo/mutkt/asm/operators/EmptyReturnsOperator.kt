package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.ClassInfo
import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Opcodes.ACONST_NULL
import org.objectweb.asm.Opcodes.ANEWARRAY
import org.objectweb.asm.Opcodes.ARETURN
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.NEWARRAY
import org.objectweb.asm.Opcodes.POP
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.TypeInsnNode

private const val STRING = "Ljava/lang/String;"

/** Replaces a returned array, List, Set, Map or Collection with an empty one, and a non-null String with "". */
internal object EmptyReturnsOperator : Operator {
    override val id = OperatorId("empty-returns")

    override fun sites(
        method: MethodNode,
        cls: ClassInfo,
    ): List<Site> {
        val string = isNonNullString(method, cls)
        if (!string && !hasEmptyValue(method.returned)) return emptyList()
        val targets = jumpTargets(method)
        return method.instructions
            .filter { it.opcode == ARETURN }
            .filterNot { returnsEmptyAlready(it, targets) }
            // A non-null String method returns null only after a throw (a lateinit getter's): unreachable.
            .filterNot { string && it.previous?.opcode == ACONST_NULL }
            .map { Site(it, if (string) "areturn -> \"\"" else "areturn -> empty") }
    }

    // The original ARETURN stays and returns the pushed empty value.
    override fun apply(
        method: MethodNode,
        site: Site,
    ) = method.instructions.insertBefore(
        site.instruction,
        InsnList().apply {
            add(InsnNode(POP))
            add(if (returnsString(method)) InsnList().apply { add(LdcInsnNode("")) } else emptyValue(method.returned))
        },
    )

    // A nullable or unknown String return is null-returns' site; "" is the Kotlin-meaningful mutant of a non-null one.
    // A name with '-' is mangled for a value class: its String may be a value class's underlying value, whose
    // init block "" would bypass.
    private fun isNonNullString(
        method: MethodNode,
        cls: ClassInfo,
    ) = returnsString(method) && cls.declaresNonNull(method) && '-' !in method.name

    private fun returnsString(method: MethodNode) = method.returned == STRING

    private val MethodNode.returned get() = Type.getReturnType(desc).descriptor

    private fun jumpTargets(method: MethodNode): Set<LabelNode> =
        buildSet {
            method.instructions.forEach {
                when (it) {
                    is JumpInsnNode -> add(it.label)
                    is TableSwitchInsnNode -> addAll(it.labels + it.dflt)
                    is LookupSwitchInsnNode -> addAll(it.labels + it.dflt)
                }
            }
            method.tryCatchBlocks.forEach { add(it.handler) }
        }

    // Line-number labels sit between the value and the return; a jump target or frame there is a merge point,
    // where the value may not be empty, so the site stays.
    private fun returnsEmptyAlready(
        ret: AbstractInsnNode,
        targets: Set<LabelNode>,
    ): Boolean {
        var previous = ret.previous
        while (previous is LineNumberNode ||
            (previous is LabelNode && previous !in targets)
        ) {
            previous = previous.previous
        }
        return when (previous) {
            is MethodInsnNode -> previous.isEmptyFactoryCall()
            is TypeInsnNode -> previous.opcode == ANEWARRAY && previous.previous?.opcode == ICONST_0
            is IntInsnNode -> previous.opcode == NEWARRAY && previous.previous?.opcode == ICONST_0
            is LdcInsnNode -> previous.cst == ""
            else -> false
        }
    }
}
