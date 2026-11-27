package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.CoverageInstrumenter
import com.github.rodrigotimoteo.mutkt.application.port.Instrumented
import com.github.rodrigotimoteo.mutkt.application.port.Probe
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantId
import org.objectweb.asm.MethodTooLargeException
import org.objectweb.asm.Opcodes.ACC_INTERFACE
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

/**
 * ASM [CoverageInstrumenter]: sets a probe right before the instructions each mutant changes (finally copies
 * included), so a hit means execution reached the site. Mutants that change the same instructions share a probe.
 * Each class fetches its probe array from [registry], the binary name of a class on the test JVM's system class path
 * with a public static `probes(String className, int size): boolean[]`. [classpath] is the lookup given to
 * [AsmMutantGenerator], so sites and frames resolve the same way.
 *
 * Interfaces (which cannot hold a private static field) and classes whose probes would push a method over the JVM
 * size limit stay uninstrumented, and sites in a `lazy { }` created while a class initialises stay unprobed (a hit
 * there names only the first test class that read the property); their mutants are unmapped.
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
        val probed =
            mutants.filterNot(once).groupBy { it.className }.toSortedMap().mapNotNull { (name, inClass) ->
                val bytes = requireNotNull(byName[name]) { "No class $name for mutant ${inClass.first().id}" }
                probed(name, parse(bytes), inClass, hierarchy)
            }
        return Instrumented(
            classes = probed.map { it.first },
            probes = probed.flatMap { it.second.entries }.associate { it.key to it.value },
        )
    }

    private fun probed(
        name: String,
        node: ClassNode,
        mutants: List<Mutant>,
        hierarchy: Hierarchy,
    ): Pair<ClassFile, Map<MutantId, Probe>>? {
        if (node.access and ACC_INTERFACE != 0) return null
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
        node.addProbeArray(registry, name, spots.size)
        return try {
            ClassFile(name, HierarchyClassWriter(hierarchy).also(node::accept).toByteArray()) to probes
        } catch (_: MethodTooLargeException) {
            null
        }
    }

    private fun operatorOf(mutant: Mutant): Operator =
        requireNotNull(Operators.all.firstOrNull { it.id == mutant.operator }) {
            "Unknown operator ${mutant.operator} of mutant ${mutant.id}"
        }
}
