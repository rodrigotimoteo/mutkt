package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.asm.filters.finallyTwins
import org.objectweb.asm.tree.MethodNode

/** Applies the site [target] (from [discover]) of [op] to a fresh parse of [bytes]. */
internal fun mutate(
    bytes: ByteArray,
    op: Operator,
    target: Found,
    hierarchy: Hierarchy,
): ByteArray {
    val node = parse(bytes)
    val cls = node.info(hierarchy)
    check(target.className == node.name) { "Site is for ${target.className} but the class is ${node.name}" }
    val method =
        checkNotNull(
            node.mutableMethods().singleOrNull { it.name == target.methodName && it.desc == target.descriptor },
        ) {
            "No mutable method ${node.name}.${target.methodName}${target.descriptor}"
        }
    val sites = sitesOf(op, cls, method)
    check(target.ordinal in sites.indices) {
        "No ordinal ${target.ordinal} in ${node.name}.${target.methodName}${target.descriptor} (${sites.size} sites)"
    }
    val site = sites[target.ordinal]
    val twins = twinSites(op, cls, method, site)
    op.apply(method, site)
    twins.forEach { op.apply(method, it) }
    return HierarchyClassWriter(hierarchy).also(node::accept).toByteArray()
}

// The compiler copies a finally body to every exit; the copies are one logical site, so they change together.
// A twin the operator does not report with the same description (its context differs) is left as written.
// Twins are taken from the operator's unfiltered sites: the other filters judge the first copy only, because
// the copies of one body are noise, or not, together.
private fun twinSites(
    op: Operator,
    cls: ClassInfo,
    method: MethodNode,
    site: Site,
): List<Site> {
    val copies = finallyTwins(method)[site.instruction].orEmpty().toSet()
    return op.sites(method, cls).filter { it.instruction in copies && it.description == site.description }
}
