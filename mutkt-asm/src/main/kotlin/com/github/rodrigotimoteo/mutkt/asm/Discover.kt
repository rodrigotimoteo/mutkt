package com.github.rodrigotimoteo.mutkt.asm

import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.LineNumberNode

/**
 * Lists every site of [op] in the mutable, unsuppressed methods of the class [bytes], in method then
 * instruction order. Suppression removes whole methods, so ordinals of the remaining ones are unaffected.
 */
internal fun discover(
    bytes: ByteArray,
    op: Operator,
    suppressions: Suppressions? = null,
): List<Found> {
    val node = parse(bytes)
    // The operator contract test calls this without suppressions on purpose: it checks operators, not filtering.
    val methods =
        node.methods
            .filter(::isMutable)
            .filter { suppressions?.covers(node.name, it.name, it.desc, op.id) != true }
    return methods.flatMap { method ->
        op.sites(method).mapIndexed { ordinal, site ->
            Found(node.name, method.name, method.desc, ordinal, lineOf(site.instruction), site.description)
        }
    }
}

private fun lineOf(instruction: AbstractInsnNode): Int? =
    generateSequence(instruction) { it.previous }.filterIsInstance<LineNumberNode>().firstOrNull()?.line
