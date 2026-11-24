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
    val method =
        checkNotNull(
            node.methods.singleOrNull {
                isMutable(it) &&
                    it.name == target.methodName &&
                    it.desc == target.descriptor
            },
        ) {
            "No mutable method ${node.name}.${target.methodName}${target.descriptor}"
        }
    val sites = op.sites(method)
    check(target.ordinal in sites.indices) {
        "No ordinal ${target.ordinal} in ${node.name}.${target.methodName}${target.descriptor} (${sites.size} sites)"
    }
    op.apply(method, sites[target.ordinal])
    return HierarchyClassWriter(hierarchy).also(node::accept).toByteArray()
}
