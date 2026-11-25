package com.github.rodrigotimoteo.mutkt.asm.filters

import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.MethodNode

internal fun realBefore(insn: AbstractInsnNode): AbstractInsnNode? =
    generateSequence(insn.previous) { it.previous }.firstOrNull { it.opcode >= 0 }

internal const val FUNCTION_MARKER = "\$i\$f\$"
internal const val LAMBDA_MARKER = "\$i\$a\$"

/**
 * kotlinc brackets every inlined function body with a local variable `$i$f$name` and every inlined lambda body
 * with `$i$a$...`. Maps each instruction to the name of the innermost marker around it; instructions outside
 * any marker are absent. Needs debug info: without local variables the map is empty.
 */
internal fun innermostMarkers(method: MethodNode): Map<AbstractInsnNode, String> {
    val regions =
        method.localVariables
            .orEmpty()
            .filter { it.name.startsWith(FUNCTION_MARKER) || it.name.startsWith(LAMBDA_MARKER) }
            .map { marker ->
                val inside =
                    generateSequence<AbstractInsnNode>(
                        marker.start,
                    ) { it.next }.takeWhile { it !== marker.end }
                marker.name to inside.toList()
            }
    val innermost = HashMap<AbstractInsnNode, String>()
    // Outer regions first, so an inner one overwrites the instructions it shares.
    regions.sortedByDescending { it.second.size }.forEach { (name, insns) -> insns.forEach { innermost[it] = name } }
    return innermost
}
