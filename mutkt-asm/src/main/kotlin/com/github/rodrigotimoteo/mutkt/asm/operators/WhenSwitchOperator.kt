package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.ClassInfo
import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.asm.filters.isStringCall
import com.github.rodrigotimoteo.mutkt.asm.filters.realAfter
import com.github.rodrigotimoteo.mutkt.asm.filters.realBefore
import com.github.rodrigotimoteo.mutkt.asm.ordinalOf
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.GETFIELD
import org.objectweb.asm.Opcodes.IFNE
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TableSwitchInsnNode

private const val SUSPEND_TAIL = "Lkotlin/coroutines/Continuation;)Ljava/lang/Object;"

private class SwitchBranch(
    val site: Site,
    val rewrite: () -> Unit,
)

// A String `when` case: the quoted constant of one `s.equals("constant")` check and the jump acting on its result.
private typealias Guard = Pair<String, JumpInsnNode>

/**
 * Changes one branch of a `when` compiled to a `TABLESWITCH`/`LOOKUPSWITCH` over an Int, Char or String: the
 * cases of one body (or one String constant) go to the default, or the default goes to the first case's body.
 * Switches on an `ordinal()` call (enum `when`) and coroutine state machines are not sites.
 */
internal object WhenSwitchOperator : Operator {
    override val id = OperatorId("when-switch")

    override fun sites(
        method: MethodNode,
        cls: ClassInfo,
    ): List<Site> = branches(method).map { it.site }

    override fun apply(
        method: MethodNode,
        site: Site,
    ) = branches(method).single { it.site == site }.rewrite()
}

// Any `ordinal()` selector is skipped without asking whether its owner is an enum, so sites never depend on
// which classes the generator can read.
private fun branches(method: MethodNode): List<SwitchBranch> =
    method.instructions
        .filter { it is TableSwitchInsnNode || it is LookupSwitchInsnNode }
        .filterNot { ordinalOf(it) != null || isStateMachine(method, it) }
        .flatMap(::switchBranches)

// A suspend function resumes at its last suspension point through a switch on its continuation's `label`.
private fun isStateMachine(
    method: MethodNode,
    switch: AbstractInsnNode,
): Boolean {
    val field = realBefore(switch) as? FieldInsnNode
    val isLabel = field?.opcode == GETFIELD && field.name == "label" && field.desc == "I"
    return isLabel && (method.name == "invokeSuspend" || method.desc.endsWith(SUSPEND_TAIL))
}

// Cases that already go to the default are not a branch, since redirecting them would change nothing.
private fun switchBranches(switch: AbstractInsnNode): List<SwitchBranch> {
    val (keys, labels, default) =
        when (switch) {
            is TableSwitchInsnNode -> Triple((switch.min..switch.max).toList(), switch.labels, switch.dflt)
            is LookupSwitchInsnNode -> Triple(switch.keys.toList(), switch.labels, switch.dflt)
            else -> error("Not a switch: $switch")
        }
    val stringHash = realBefore(switch).isStringCall("hashCode", "()I")
    val cases = keys.zip(labels).filter { it.second !== default }
    val guards = cases.associate { (_, label) -> label to guardsOf(stringHash, label) }
    return caseBranches(switch, cases, guards, labels, default) + listOfNotNull(defaultBranch(switch, cases, guards))
}

// One site per body (cases that share a label, as in `1, 2 -> ...`, move together); in a String `when` one site per
// constant, whose check then jumps to the default.
private fun caseBranches(
    switch: AbstractInsnNode,
    cases: List<Pair<Int, LabelNode>>,
    guards: Map<LabelNode, List<Guard>?>,
    labels: MutableList<LabelNode>,
    default: LabelNode,
): List<SwitchBranch> =
    cases.groupBy({ it.second }, { it.first }).flatMap { (body, group) ->
        val checks = guards.getValue(body).orEmpty()
        if (checks.isEmpty()) {
            listOf(
                SwitchBranch(Site(switch, "when case ${group.joinToString(",")} -> default")) {
                    labels.replaceAll { label -> if (label === body) default else label }
                },
            )
        } else {
            checks.map { (constant, jump) ->
                SwitchBranch(Site(switch, "when case $constant -> default")) {
                    jump.label =
                        default
                }
            }
        }
    }

// The default goes to the case with the lowest key that does not already go there; in a String `when` to the body
// of the first constant checked under that hash (the check itself would fail and come back to the default).
private fun defaultBranch(
    switch: AbstractInsnNode,
    cases: List<Pair<Int, LabelNode>>,
    guards: Map<LabelNode, List<Guard>?>,
): SwitchBranch? {
    val first = cases.firstOrNull()
    val checks = first?.let { guards.getValue(it.second) } ?: return null
    val (name, body) =
        checks.firstOrNull()?.let { (constant, jump) -> constant to jump.label }
            ?: first.let { (key, label) -> key.toString() to label }
    return SwitchBranch(Site(switch, "when default -> case $name")) {
        when (switch) {
            is TableSwitchInsnNode -> switch.dflt = body
            is LookupSwitchInsnNode -> switch.dflt = body
        }
    }
}

// Kotlin checks every constant that shares a hash in one chain under its case; each check jumps to its body (`IFNE`).
// Empty for an Int switch or a case without checks; null for checks that skip on a mismatch (`IFEQ`, javac's first
// switch, which only sets an index for a second switch): their constants have no body of their own.
private fun guardsOf(
    stringHash: Boolean,
    caseLabel: LabelNode,
): List<Guard>? {
    if (!stringHash) return emptyList()
    val chain = generateSequence(guardAt(realAfter(caseLabel))) { guardAt(realAfter(it.second)) }.toList()
    return chain.takeIf { checks -> checks.all { it.second.opcode == IFNE } }
}

// `ALOAD s; LDC "constant"; INVOKEVIRTUAL String.equals; IF*` starting at [load].
private fun guardAt(load: AbstractInsnNode?): Guard? {
    val constant = load?.takeIf { it.opcode == ALOAD }?.let(::realAfter) as? LdcInsnNode
    val equals = constant?.let(::realAfter)?.takeIf { it.isStringCall("equals", "(Ljava/lang/Object;)Z") }
    val jump = equals?.let(::realAfter) as? JumpInsnNode
    val text = constant?.cst as? String
    return if (text != null && jump != null) "\"$text\"" to jump else null
}
