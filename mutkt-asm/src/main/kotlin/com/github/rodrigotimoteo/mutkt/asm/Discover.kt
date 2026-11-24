package com.github.rodrigotimoteo.mutkt.asm

import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.LineNumberNode

/** Lists every site of [op] in the mutable methods of the class [bytes], in method then instruction order. */
internal fun discover(
    bytes: ByteArray,
    op: Operator,
): List<Found> {
    val node = parse(bytes)
    return node.methods.filter(::isMutable).flatMap { method ->
        op.sites(method).mapIndexed { ordinal, site ->
            Found(node.name, method.name, method.desc, ordinal, lineOf(site.instruction), site.description)
        }
    }
}

private fun lineOf(instruction: AbstractInsnNode): Int? =
    generateSequence(instruction) { it.previous }.filterIsInstance<LineNumberNode>().firstOrNull()?.line
