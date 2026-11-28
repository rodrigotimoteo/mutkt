package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.ClassInfo
import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

// Callers name the multifile facade, which inherits every part class's statics.
private val OWNERS =
    setOf(
        "kotlin/collections/CollectionsKt",
        "kotlin/collections/ArraysKt",
        "kotlin/sequences/SequencesKt",
        "kotlin/text/StringsKt",
    )

// In each owner, every overload of one name has a twin with the same descriptor under the other name, so a rename
// keeps the stack as it was. min, max, minWith and maxWith compile to their OrThrow names; the plain names are the
// hidden pre-1.7 nullable versions, still called by old binaries. Overloads taking a lambda are inline and never
// called, except on Sequence, whose lazy filter and filterNot are real calls; any with a predicate pairs with all.
private val PLAIN =
    symmetric(
        "any" to "none",
        "first" to "last",
        "firstOrNull" to "lastOrNull",
        "min" to "max",
        "minOrThrow" to "maxOrThrow",
        "minOrNull" to "maxOrNull",
        "minWith" to "maxWith",
        "minWithOrThrow" to "maxWithOrThrow",
        "minWithOrNull" to "maxWithOrNull",
        "take" to "drop",
        "sorted" to "sortedDescending",
    )
private val WITH_LAMBDA = symmetric("filter" to "filterNot")
private const val FUNCTION = "Lkotlin/jvm/functions/"

private fun symmetric(vararg pairs: Pair<String, String>) = (pairs.toList() + pairs.map { (a, b) -> b to a }).toMap()

/** The stdlib function a call to [owner].[name][desc] is swapped for, or null when it is not a site. */
internal fun stdlibPartner(
    owner: String,
    name: String,
    desc: String,
): String? =
    when {
        owner !in OWNERS -> null
        FUNCTION in desc -> WITH_LAMBDA[name]
        else -> PLAIN[name]
    }

/** Swaps a Kotlin stdlib collection call for its opposite: `any`/`none`, `first`/`last`, `min`/`max`, `take`/`drop`. */
internal object StdlibSwapOperator : Operator {
    override val id = OperatorId("stdlib-swap")

    override fun sites(
        method: MethodNode,
        cls: ClassInfo,
    ): List<Site> =
        method.instructions
            .filterIsInstance<MethodInsnNode>()
            .filter { it.opcode == INVOKESTATIC }
            .mapNotNull { call ->
                partner(call)?.let { Site(call, "${call.owner.substringAfterLast('/')}.${call.name} -> $it") }
            }

    override fun apply(
        method: MethodNode,
        site: Site,
    ) {
        val call = site.instruction as MethodInsnNode
        call.name = checkNotNull(partner(call)) { "${call.name} has no stdlib partner" }
    }

    private fun partner(call: MethodInsnNode) = stdlibPartner(call.owner, call.name, call.desc)
}
