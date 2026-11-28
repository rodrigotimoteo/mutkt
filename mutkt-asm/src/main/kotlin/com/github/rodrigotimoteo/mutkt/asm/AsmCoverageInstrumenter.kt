package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.CoverageInstrumenter
import com.github.rodrigotimoteo.mutkt.application.port.Instrumented
import com.github.rodrigotimoteo.mutkt.application.port.Probe
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantId
import org.objectweb.asm.ClassTooLargeException
import org.objectweb.asm.MethodTooLargeException
import org.objectweb.asm.Opcodes.ACC_INTERFACE
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

/**
 * ASM [CoverageInstrumenter]: sets a probe right before the instructions each mutant changes (finally copies
 * included), so a hit means execution reached the site, and one on entry to every method of every class, so a hit
 * means execution reached the class (the last slot of its probe array, shared by all its methods). Mutants that change
 * the same instructions share a probe.
 * Each class fetches its probe array from [registry], the binary name of a class on the test JVM's system class path
 * with a public static `probes(String className, int size): boolean[]`. [classpath] is the lookup given to
 * [AsmMutantGenerator], so sites and frames resolve the same way.
 *
 * Interfaces (which cannot hold a private static field), classes without code and classes whose probes would push a
 * method or the class over a JVM size limit (or whose frames need a type [classpath] does not know) stay
 * uninstrumented and are listed as unprobed, as is a class without mutants that fails to rewrite. Sites in a
 * `lazy { }` created while a class initialises stay unprobed (a hit there names only the first test class that read
 * the property); their mutants are unmapped.
 */
public class AsmCoverageInstrumenter(
    private val registry: String,
    private val classpath: (String) -> ByteArray?,
) : CoverageInstrumenter {
    override fun instrument(
        classes: List<ClassFile>,
        mutants: List<Mutant>,
    ): Instrumented {
        val byName = classes.associate { it.name to it.bytes }
        val hierarchy = hierarchyOf(byName, classpath)
        val once = inLazyInitialiser(byName)
        val sited = mutants.filterNot(once).groupBy { it.className }
        for ((name, inClass) in sited) require(name in byName) { "No class $name for mutant ${inClass.first().id}" }
        val probed =
            byName.toSortedMap().mapValues { (name, bytes) -> probed(name, bytes, sited[name].orEmpty(), hierarchy) }
        val done = probed.values.filterNotNull()
        return Instrumented(
            classes = done.map { it.first },
            probes = done.flatMap { it.second.entries }.associate { it.key to it.value },
            unprobed = probed.filterValues { it == null }.keys,
        )
    }

    // Null when the class cannot be probed. A class without mutants that fails to rewrite for any reason keeps its
    // original bytes: only its reach is lost, not the coverage of every other class.
    @Suppress("TooGenericExceptionCaught")
    private fun probed(
        name: String,
        bytes: ByteArray,
        mutants: List<Mutant>,
        hierarchy: Hierarchy,
    ): Pair<ClassFile, Map<MutantId, Probe>>? =
        try {
            rewrite(name, parse(bytes), mutants, hierarchy)
        } catch (e: Exception) {
            if (mutants.isEmpty() || e.isProbeLimit()) null else throw e
        }

    private fun rewrite(
        name: String,
        node: ClassNode,
        mutants: List<Mutant>,
        hierarchy: Hierarchy,
    ): Pair<ClassFile, Map<MutantId, Probe>>? {
        val entries = node.methods.filter { it.instructions.size() > 0 }
        if (node.access and ACC_INTERFACE != 0 || entries.isEmpty()) return null
        val (probes, sites) = siteProbes(name, node, mutants, hierarchy)
        // Every method sets the one last slot, after the site probes: reach is per class, and a mutant's probe does not
        // depend on how many methods its class has.
        entries.forEach { it.insertEntryProbe(node.name, sites) }
        node.addProbeArray(registry, name, sites + 1)
        return ClassFile(name, HierarchyClassWriter(hierarchy).also(node::accept).toByteArray()) to probes
    }

    // Inserts one probe per distinct site of [mutants]; each mutant's probe and how many were inserted.
    private fun siteProbes(
        name: String,
        node: ClassNode,
        mutants: List<Mutant>,
        hierarchy: Hierarchy,
    ): Pair<Map<MutantId, Probe>, Int> {
        if (mutants.isEmpty()) return emptyMap<MutantId, Probe>() to 0
        val cls = node.info(hierarchy)
        // Found before any probe goes in, since inserting shifts what the operators would see.
        val spots = LinkedHashMap<Pair<MethodNode, List<AbstractInsnNode>>, Int>()
        val probes =
            mutants.associate { mutant ->
                val id = mutant.id
                val located = node.locate(operatorOf(mutant), cls, id.methodName, id.methodDescriptor, id.ordinal)
                val spot = located.method to located.sites.map { it.instruction }
                mutant.id to Probe(name, spots.getOrPut(spot) { spots.size })
            }
        spots.forEach { (spot, index) -> spot.first.insertProbe(node.name, spot.second, index) }
        return probes to spots.size
    }

    private fun Exception.isProbeLimit() =
        this is MethodTooLargeException || this is ClassTooLargeException || this is UnresolvedTypeException

    private fun operatorOf(mutant: Mutant): Operator =
        requireNotNull(Operators.all.firstOrNull { it.id == mutant.operator }) {
            "Unknown operator ${mutant.operator} of mutant ${mutant.id}"
        }
}
