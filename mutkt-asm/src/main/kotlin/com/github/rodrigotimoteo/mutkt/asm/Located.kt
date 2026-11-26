package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.asm.filters.finallyTwins
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

/** The [method] holding a site and the [sites] its mutation changes: the site itself, then its finally twins. */
internal class Located(
    val method: MethodNode,
    val sites: List<Site>,
)

/** Finds site [ordinal] of [op] in the mutable method [methodName][descriptor] of this class ([cls]). */
internal fun ClassNode.locate(
    op: Operator,
    cls: ClassInfo,
    methodName: String,
    descriptor: String,
    ordinal: Int,
): Located {
    val method =
        checkNotNull(mutableMethods().singleOrNull { it.name == methodName && it.desc == descriptor }) {
            "No mutable method $name.$methodName$descriptor"
        }
    val sites = sitesOf(op, cls, method)
    check(ordinal in sites.indices) { "No ordinal $ordinal in $name.$methodName$descriptor (${sites.size} sites)" }
    val site = sites[ordinal]
    return Located(method, listOf(site) + twinSites(op, cls, method, site))
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
