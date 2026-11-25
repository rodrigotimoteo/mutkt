package com.github.rodrigotimoteo.mutkt.asm.filters

import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.ASTORE
import org.objectweb.asm.Opcodes.GOTO
import org.objectweb.asm.Opcodes.ILOAD
import org.objectweb.asm.Opcodes.ISTORE
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.IincInsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode

/**
 * Whether the [size] instructions at [copy] are the same code as those at [template]: same opcodes and
 * operands, locals the body declares renamed one-to-one (the others kept), jumps landing at the same offset
 * inside the copy.
 */
internal class CopyMatcher(
    private val code: Code,
    private val template: Int,
    private val copy: Int,
    private val size: Int,
) {
    private val slots = HashMap<Int, Int>()
    private val reverse = HashMap<Int, Int>()

    // Slots the body reads before writing it hold values from before the try: every copy reads the same slot.
    private val external: Set<Int> = externalSlots(code.insns.subList(template, template + size))

    // Where the compiler jumps from inside a copy straight to the end of the finally body, past the copy.
    private val skipTarget: Int? =
        (code.insns.getOrNull(copy + size) as? JumpInsnNode)
            ?.takeIf {
                it.opcode == GOTO
            }?.let { code.at(it.label) }

    fun matches(): Boolean =
        copy + size <= code.insns.size && (0 until size).all { same(code.insns[template + it], code.insns[copy + it]) }

    private fun same(
        a: AbstractInsnNode,
        b: AbstractInsnNode,
    ): Boolean = a.opcode == b.opcode && a.javaClass == b.javaClass && operands(a) == operands(b) && sameFlow(a, b)

    // Local slots and jump targets, which differ between copies in a way that must be consistent.
    private fun sameFlow(
        a: AbstractInsnNode,
        b: AbstractInsnNode,
    ): Boolean =
        when (a) {
            is VarInsnNode -> slot(a.`var`, (b as VarInsnNode).`var`)
            is IincInsnNode -> slot(a.`var`, (b as IincInsnNode).`var`) && a.incr == b.incr
            is JumpInsnNode -> sameTarget(a.label, template, (b as JumpInsnNode).label, copy)
            is TableSwitchInsnNode -> sameSwitch(a, b as TableSwitchInsnNode)
            is LookupSwitchInsnNode -> sameSwitch(a, b as LookupSwitchInsnNode)
            else -> true
        }

    private fun slot(
        a: Int,
        b: Int,
    ): Boolean = (a !in external || a == b) && slots.getOrPut(a) { b } == b && reverse.getOrPut(b) { a } == a

    // Offset of a jump target inside its copy, or -1 when it leaves the copy anywhere but the end.
    private fun offset(
        label: LabelNode,
        start: Int,
        exit: Int?,
    ): Int {
        val at = code.at(label)
        return when {
            at - start in 0..size -> at - start
            at == exit -> size
            else -> -1
        }
    }

    private fun sameTarget(
        a: LabelNode,
        aStart: Int,
        b: LabelNode,
        bStart: Int,
    ): Boolean = offset(a, aStart, null).let { it >= 0 && it == offset(b, bStart, skipTarget) }

    private fun sameSwitch(
        a: TableSwitchInsnNode,
        b: TableSwitchInsnNode,
    ) = a.min == b.min && a.max == b.max && sameTargets(a.dflt, a.labels, b.dflt, b.labels)

    private fun sameSwitch(
        a: LookupSwitchInsnNode,
        b: LookupSwitchInsnNode,
    ) = a.keys == b.keys && sameTargets(a.dflt, a.labels, b.dflt, b.labels)

    private fun sameTargets(
        aDefault: LabelNode,
        aLabels: List<LabelNode>,
        bDefault: LabelNode,
        bLabels: List<LabelNode>,
    ) = aLabels.size == bLabels.size &&
        (listOf(aDefault) + aLabels).zip(listOf(bDefault) + bLabels).all { (x, y) -> sameTarget(x, template, y, copy) }
}

private fun externalSlots(body: List<AbstractInsnNode>): Set<Int> {
    val written = HashSet<Int>()
    val read = HashSet<Int>()
    body.forEach {
        when {
            it is IincInsnNode -> if (it.`var` !in written) read += it.`var`
            it is VarInsnNode && it.opcode in ISTORE..ASTORE -> written += it.`var`
            it is VarInsnNode && it.opcode in ILOAD..ALOAD -> if (it.`var` !in written) read += it.`var`
        }
    }
    return read
}

// What an instruction names besides local slots and jump targets: all of it must be equal in every copy.
private fun operands(node: AbstractInsnNode): List<Any?> =
    when (node) {
        is MethodInsnNode -> listOf(node.owner, node.name, node.desc, node.itf)
        is FieldInsnNode -> listOf(node.owner, node.name, node.desc)
        is TypeInsnNode -> listOf(node.desc)
        is IntInsnNode -> listOf(node.operand)
        is LdcInsnNode -> listOf(node.cst)
        is InvokeDynamicInsnNode -> listOf(node.name, node.desc, node.bsm, node.bsmArgs.toList())
        is MultiANewArrayInsnNode -> listOf(node.desc, node.dims)
        else -> emptyList()
    }
