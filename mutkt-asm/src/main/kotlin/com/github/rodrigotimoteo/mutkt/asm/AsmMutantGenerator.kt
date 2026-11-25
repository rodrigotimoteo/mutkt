package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.MutantGenerator
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantId

private class Planned(
    val mutant: Mutant,
    val operator: Operator,
    val found: Found,
    val original: ByteArray,
)

private class Plan(
    val byId: Map<MutantId, Planned>,
    val hierarchy: Hierarchy,
)

/**
 * ASM [MutantGenerator]. [classpath] returns class bytes by internal name (`a/b/Foo`) for types outside the
 * analysed classes (dependencies); the analysed classes and the JDK are resolved without it. It must be
 * thread-safe: [materialize] may run concurrently.
 *
 * Suppression names are validated against the full catalog, not the enabled subset.
 *
 * [discover] applies every site eagerly (fail-fast, and it drops mutants that change nothing), so a broken
 * operator fails the run before any test executes. A class whose frames need a type that neither [classpath] nor
 * the JDK running the generator knows yields no mutants; [onSkip] receives one message naming the class and type.
 */
public class AsmMutantGenerator internal constructor(
    private val operators: List<Operator>,
    private val onSkip: (String) -> Unit,
    private val classpath: (String) -> ByteArray?,
) : MutantGenerator {
    public constructor(onSkip: (String) -> Unit, classpath: (String) -> ByteArray?) :
        this(Operators.defaults, onSkip, classpath)

    /** Enables only these operator ids (empty means the defaults); an unknown id fails with the valid ones. */
    public constructor(operatorIds: Set<String>, onSkip: (String) -> Unit, classpath: (String) -> ByteArray?) :
        this(Operators.byId(operatorIds), onSkip, classpath)

    @Volatile
    private var plan: Plan? = null

    override fun discover(
        classes: List<ClassFile>,
        targets: List<ClassFile>,
    ): List<Mutant> {
        val byName = classes.associate { it.name to it.bytes }
        val hierarchy: Hierarchy = { type -> byName[type.replace('/', '.')] ?: classpath(type) }
        val suppressions =
            Suppressions(
                classes.map { it.bytes },
                targets.mapTo(mutableSetOf()) { it.name.replace('.', '/') },
                Operators.all.map { it.id },
            )
        val planned = targets.sortedBy { it.name }.flatMap { plannedOrSkipped(it, hierarchy, suppressions) }
        val byId = planned.associateBy { it.mutant.id }
        require(byId.size == planned.size) {
            "Duplicate mutant ids (duplicate class names or operator ids): " +
                planned.groupBy { it.mutant.id }.filterValues { it.size > 1 }.keys
        }
        plan = Plan(byId, hierarchy)
        return planned.map { it.mutant }
    }

    override fun materialize(mutant: Mutant): ByteArray {
        val current = checkNotNull(plan) { "discover must run before materialize" }
        val planned = requireNotNull(current.byId[mutant.id]) { "Unknown mutant ${mutant.id}" }
        return mutate(planned.original, planned.operator, planned.found, current.hierarchy)
    }

    // Frames are recomputed for the whole class, so one type missing from the hierarchy (on no classpath given, or
    // a JDK type newer than this JVM) rules out every mutant of it; the rest of the run goes on.
    private fun plannedOrSkipped(
        target: ClassFile,
        hierarchy: Hierarchy,
        suppressions: Suppressions,
    ): List<Planned> =
        try {
            plannedFor(target.bytes, hierarchy, suppressions)
        } catch (e: UnresolvedTypeException) {
            onSkip("Skipped ${target.name}: ${e.message}")
            emptyList()
        }

    // A mutant whose class equals the plain rewrite of the original changed nothing (equal-bytes operators
    // are dropped); comparing with the raw input would never match because the rewrite recomputes frames.
    private fun plannedFor(
        bytes: ByteArray,
        hierarchy: Hierarchy,
        suppressions: Suppressions,
    ): List<Planned> {
        val unchanged by lazy { HierarchyClassWriter(hierarchy).also { parse(bytes).accept(it) }.toByteArray() }
        val sourceFile = parse(bytes).sourceFile
        return operators.flatMap { op ->
            discover(bytes, op, suppressions, hierarchy)
                .filterNot { mutate(bytes, op, it, hierarchy).contentEquals(unchanged) }
                .map { Planned(mutantOf(op, it, sourceFile), op, it, bytes) }
        }
    }

    private fun mutantOf(
        op: Operator,
        found: Found,
        sourceFile: String?,
    ): Mutant {
        val className = found.className.replace('/', '.')
        return Mutant(
            id = MutantId(op.id, className, found.methodName, found.descriptor, found.ordinal),
            operator = op.id,
            className = className,
            methodName = found.methodName,
            methodDescriptor = found.descriptor,
            line = found.line,
            description = found.description,
            sourceFile = sourceFile,
        )
    }
}
