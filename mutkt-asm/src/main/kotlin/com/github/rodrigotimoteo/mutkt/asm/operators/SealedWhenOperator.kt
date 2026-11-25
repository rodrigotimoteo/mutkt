package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.ClassInfo
import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.asm.filters.finallyTwins
import com.github.rodrigotimoteo.mutkt.asm.isMappedOrdinal
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.Opcodes.IALOAD
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.INSTANCEOF
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.Opcodes.INVOKEVIRTUAL
import org.objectweb.asm.Opcodes.POP
import org.objectweb.asm.Opcodes.POP2
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import java.util.IdentityHashMap

private const val INTRINSICS = "kotlin/jvm/internal/Intrinsics"

// A branch is one mutant: its [site] and the rewrite that performs it on the method the branch was found in.
private class Branch(
    val site: Site,
    val rewrite: (MethodNode) -> Unit,
)

private fun AbstractInsnNode.previousReal(): AbstractInsnNode? =
    generateSequence(previous) { it.previous }.firstOrNull { it.opcode >= 0 }

/**
 * Makes one `when` branch unreachable. Type tests (`INSTANCEOF`, or `Intrinsics.areEqual` against an object's
 * `INSTANCE`) on a local that is tested at least twice in the method become false, so the branch is skipped
 * for the next one or the else path. Every case of an enum `when` switch (an `ordinal()` selector) that
 * leads to one body is redirected to the switch default.
 */
internal object SealedWhenOperator : Operator {
    override val id = OperatorId("sealed-when")

    override fun sites(
        method: MethodNode,
        cls: ClassInfo,
    ): List<Site> = branches(method) { isEnumOrdinal(it, cls) }.map { it.site }

    override fun apply(
        method: MethodNode,
        site: Site,
    ) = branches(method) { true }.single { it.site == site }.rewrite(method)

    // sortedBy is stable: the branches of one switch keep their case order.
    // [enumOrdinal] decides which `ordinal()` calls are an enum's; [apply] works on a site that already passed it.
    private fun branches(
        method: MethodNode,
        enumOrdinal: (MethodInsnNode) -> Boolean,
    ): List<Branch> =
        (typeTests(method) + enumSwitches(method, enumOrdinal)).sortedBy {
            method.instructions.indexOf(it.site.instruction)
        }
}

// The local is the temporary Kotlin stores the `when` subject in. One test of a local is a plain `is` check; the
// copies of a finally body are the same test, so they count once and share the local of the first copy.
private fun typeTests(method: MethodNode): List<Branch> {
    val tests = method.instructions.mapNotNull(::typeTest)
    val first = IdentityHashMap<AbstractInsnNode, AbstractInsnNode>()
    finallyTwins(method).forEach { (original, copies) -> copies.forEach { first[it] = original } }
    val localOf = tests.associate { it.second.site.instruction to it.first }
    val logical = { test: Pair<Int, Branch> -> first[test.second.site.instruction] ?: test.second.site.instruction }
    val clusterLocal = { test: Pair<Int, Branch> -> localOf[logical(test)] ?: test.first }
    val perLocal = tests.groupBy(clusterLocal).mapValues { (_, group) -> group.map(logical).distinct().size }
    return tests.filter { perLocal.getValue(clusterLocal(it)) > 1 }.map { it.second }
}

private fun typeTest(insn: AbstractInsnNode): Pair<Int, Branch>? =
    when {
        insn.opcode == INSTANCEOF ->
            subject(insn)?.let { it to falseBranch(insn, POP, "instanceof ${(insn as TypeInsnNode).desc}") }
        insn is MethodInsnNode ->
            objectInstance(insn)?.let { obj ->
                subject(obj)?.let { it to falseBranch(insn, POP2, "equals ${obj.owner}") }
            }
        else -> null
    }

private fun subject(test: AbstractInsnNode): Int? =
    (test.previousReal() as? VarInsnNode)?.takeIf { it.opcode == ALOAD }?.`var`

// The `INSTANCE` field load of the `areEqual(subject, Object.INSTANCE)` that Kotlin emits for an object branch.
private fun objectInstance(call: MethodInsnNode): FieldInsnNode? =
    (call.previousReal() as? FieldInsnNode)?.takeIf {
        call.opcode == INVOKESTATIC &&
            call.owner == INTRINSICS &&
            call.name == "areEqual" &&
            call.desc == "(Ljava/lang/Object;Ljava/lang/Object;)Z" &&
            it.opcode == GETSTATIC &&
            it.name == "INSTANCE" &&
            it.desc == "L${it.owner};"
    }

private fun falseBranch(
    test: AbstractInsnNode,
    pop: Int,
    what: String,
) = Branch(Site(test, "$what -> false")) { method ->
    method.instructions.insertBefore(
        test,
        InsnList().apply {
            add(InsnNode(pop))
            add(InsnNode(ICONST_0))
        },
    )
    method.instructions.remove(test)
}

private fun enumSwitches(
    method: MethodNode,
    enumOrdinal: (MethodInsnNode) -> Boolean,
): List<Branch> =
    method.instructions
        .filter { it is TableSwitchInsnNode || it is LookupSwitchInsnNode }
        .filter { switch -> ordinalOf(switch)?.let(enumOrdinal) == true }
        .flatMap(::switchBranches)

// The selector is `mapping[e.ordinal()]` (the `$WhenMappings` array Kotlin builds) or `e.ordinal()` itself.
// Plain Int and String switches (a `hashCode()` selector) are not `when` over a hierarchy.
private fun ordinalOf(switch: AbstractInsnNode): MethodInsnNode? {
    val selector = switch.previousReal()?.let { if (it.opcode == IALOAD) it.previousReal() else it }
    return (selector as? MethodInsnNode)?.takeIf {
        it.opcode == INVOKEVIRTUAL && it.name == "ordinal" && it.desc == "()I"
    }
}

// A method named `ordinal` is the enum's only if the compiler's mapping lookup says so or the owner is an enum
// class; an owner the generator cannot read is not assumed to be one, so a direct `when (e.ordinal)` on an
// enum from an unknown class is skipped.
private fun isEnumOrdinal(
    ordinal: MethodInsnNode,
    cls: ClassInfo,
) = isMappedOrdinal(ordinal) || cls.isEnum(ordinal.owner)

// The description names the switch key (a $WhenMappings index, not an ordinal): entry names live in another class.
// One branch per body (cases that share a label, as in `A, B -> ...`, move together); cases that already
// go to the default are not a branch, since redirecting them would change nothing.
private fun switchBranches(switch: AbstractInsnNode): List<Branch> {
    val (keys, labels, default) =
        when (switch) {
            is TableSwitchInsnNode -> Triple((switch.min..switch.max).toList(), switch.labels, switch.dflt)
            is LookupSwitchInsnNode -> Triple(switch.keys.toList(), switch.labels, switch.dflt)
            else -> error("Not a switch: $switch")
        }
    return keys
        .zip(labels)
        .filter { it.second !== default }
        .groupBy({ it.second }, { it.first })
        .map { (body, cases) ->
            Branch(Site(switch, "when switch key ${cases.joinToString(",")} -> default")) {
                labels.replaceAll { label -> if (label === body) default else label }
            }
        }
}
