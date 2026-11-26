package com.github.rodrigotimoteo.mutkt.asm

/** Applies the site [target] (from [discover]) of [op] to a fresh parse of [bytes]. */
internal fun mutate(
    bytes: ByteArray,
    op: Operator,
    target: Found,
    hierarchy: Hierarchy,
): ByteArray {
    val node = parse(bytes)
    check(target.className == node.name) { "Site is for ${target.className} but the class is ${node.name}" }
    val located = node.locate(op, node.info(hierarchy), target.methodName, target.descriptor, target.ordinal)
    located.sites.forEach { op.apply(located.method, it) }
    return HierarchyClassWriter(hierarchy).also(node::accept).toByteArray()
}
