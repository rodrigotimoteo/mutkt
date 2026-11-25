package com.github.rodrigotimoteo.mutkt.asm.filters

import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.ASTORE
import org.objectweb.asm.Opcodes.ATHROW
import org.objectweb.asm.Opcodes.GOTO
import org.objectweb.asm.Opcodes.IRETURN
import org.objectweb.asm.Opcodes.RETURN
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import java.util.Collections
import java.util.IdentityHashMap

/**
 * kotlinc copies a finally body to the normal path, to every return, break and continue exit, and to the
 * catch-any handler. Maps each instruction of the first copy to its counterparts in the other copies. A handler
 * whose copies cannot all be matched, or overlap another group, gets no entry: its copies stay separate sites.
 */
internal fun finallyTwins(method: MethodNode): Map<AbstractInsnNode, List<AbstractInsnNode>> {
    val handlers =
        method.tryCatchBlocks
            .orEmpty()
            .filter { it.type == null }
            .map { it.handler }
            .distinct()
    if (handlers.isEmpty()) return emptyMap()
    val code = Code(method)
    val twins = IdentityHashMap<AbstractInsnNode, List<AbstractInsnNode>>()
    val claimed = Collections.newSetFromMap(IdentityHashMap<AbstractInsnNode, Boolean>())
    handlers.mapNotNull { guarded { copiesOf(method, code, it) } }.forEach { copies ->
        val all = copies.flatten()
        if (all.none { it in claimed }) {
            claimed += all
            copies.first().forEachIndexed { i, rep -> twins[rep] = copies.drop(1).map { it[i] } }
        }
    }
    return twins
}

// Unexpected bytecode shapes must never fail discovery: the handler is then simply not grouped.
private inline fun <T> guarded(block: () -> T?): T? =
    try {
        block()
    } catch (_: IllegalStateException) {
        null
    } catch (_: ClassCastException) {
        null
    } catch (_: IndexOutOfBoundsException) {
        null
    } catch (_: NoSuchElementException) {
        null
    }

// The real instructions of a method (no labels, lines or frames), indexed by position.
internal class Code(
    method: MethodNode,
) {
    val insns: List<AbstractInsnNode> = method.instructions.filter { it.opcode >= 0 }
    private val labels = IdentityHashMap<LabelNode, Int>()

    init {
        var count = 0
        method.instructions.forEach {
            if (it is LabelNode) {
                labels[it] = count
            } else if (it.opcode >= 0) {
                count++
            }
        }
    }

    // Position of the first real instruction at or after the label.
    fun at(label: LabelNode): Int = labels.getValue(label)
}

private val EXITS = IRETURN..RETURN

// ALOAD e; ATHROW, which ends the handler's copy of the body.
private const val RETHROW = 2

// Each copy as its list of instructions, the first copy first; null when the handler is not a finally or a
// copy after a try range differs. The other copies are matched only if they are exactly the body.
private fun copiesOf(
    method: MethodNode,
    code: Code,
    handler: LabelNode,
): List<List<AbstractInsnNode>>? {
    val body = bodyBounds(code, code.at(handler)) ?: return null
    val size = body.last - body.first + 1
    // The first copy is the representative; copies must not share instructions.
    return copyStarts(method, code, handler, body)
        ?.takeIf { starts -> starts.zipWithNext().none { (a, b) -> b - a < size } }
        ?.map { code.insns.subList(it, it + size) }
}

private fun copyStarts(
    method: MethodNode,
    code: Code,
    handler: LabelNode,
    body: IntRange,
): List<Int>? {
    val size = body.last - body.first + 1
    val own = setOf(code.at(handler), body.first)
    val matches = { start: Int -> CopyMatcher(code, body.first, start, size).matches() }
    val strict = (rangeEnds(method, code, handler) - own).filter { it >= 0 }
    if (!strict.all(matches)) return null
    val loose = jumpsToEnd(code, body.last + RETHROW + 1).map { it - size } - own - strict.toSet()
    return (listOf(body.first) + strict + loose.filter { it >= 0 && matches(it) }).sorted()
}

// The finally body after the handler's ASTORE up to ALOAD e; ATHROW, as a range of positions; a body that
// returns or throws by itself (a return in finally) has no such end and is not matched.
private fun bodyBounds(
    code: Code,
    h: Int,
): IntRange? {
    val store = code.insns.getOrNull(h)?.takeIf { it.opcode == ASTORE } as? VarInsnNode ?: return null
    val exit =
        (h + 1 until code.insns.size).firstOrNull {
            code.insns[it].opcode in EXITS ||
                code.insns[it].opcode == ATHROW
        }
    val rethrow = exit?.takeIf { code.insns[it].opcode == ATHROW && it - RETHROW > h }
    val load = rethrow?.let { code.insns[it - 1] as? VarInsnNode }
    return if (rethrow != null && load?.opcode == ALOAD && load.`var` == store.`var`) h + 1..rethrow - RETHROW else null
}

// The compiler ends a try range where it inserts a copy; ranges inside the handler protect the handler itself.
private fun rangeEnds(
    method: MethodNode,
    code: Code,
    handler: LabelNode,
): List<Int> =
    method.tryCatchBlocks
        .filter { it.handler === handler && code.at(it.start) < code.at(handler) }
        .map { code.at(it.end) }
        .distinct()

// The normal-path copy has no range of its own when the try body ends right before it; it is the code before
// the GOTO that skips the handler. A GOTO with another instruction before it is not a copy, so it is no error.
private fun jumpsToEnd(
    code: Code,
    end: Int,
): List<Int> =
    code.insns.indices.filter { i ->
        (code.insns[i] as? JumpInsnNode)?.takeIf { it.opcode == GOTO }?.let { code.at(it.label) } == end
    }
