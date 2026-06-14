package com.github.rodrigotimoteo.mutation.mutator

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

/**
 * Main mutation engine that applies mutations to class bytecode.
 *
 * Uses ASM to visit and transform bytecode instructions. Supports multiple
 * mutation operators including arithmetic, conditional, return value, and
 * Kotlin-specific mutations.
 *
 * Example:
 * ```kotlin
 * val mutator = Mutator(MutationOperator.MVP_OPERATORS)
 * val mutations = mutator.scanMutations(classBytes)
 * val mutated = mutator.applyMutation(classBytes, mutations.first())
 * ```
 *
 * @property enabledOperators Set of mutation operators to apply
 * @see MutationOperator for available operators
 * @see MutationInfo for mutation point details
 */
class Mutator(
    private val enabledOperators: Set<MutationOperator> = MutationOperator.MVP_OPERATORS,
    private val excludedMethods: Set<String> = emptySet(),
) {
    /**
     * Scans a class for mutation points without applying mutations.
     * Returns list of potential mutations.
     */
    fun scanMutations(classBytes: ByteArray): List<MutationInfo> {
        val mutations = mutableListOf<MutationInfo>()
        val reader = ClassReader(classBytes)
        val visitor = MutationScannerVisitor(mutations, enabledOperators, excludedMethods)
        reader.accept(visitor, ClassReader.SKIP_FRAMES)
        return mutations
    }

    /**
     * Applies a specific mutation to class bytecode.
     * Returns mutated bytecode.
     */
    fun applyMutation(
        classBytes: ByteArray,
        targetMutation: MutationInfo,
    ): ByteArray {
        val selfName = readInternalName(classBytes)
        val resolver = LoadClassResolver(selfName, classBytes)
        val writer = CommonSuperClassClassWriter(resolver)
        val reader = ClassReader(classBytes)
        val visitor = MutationApplierVisitor(writer, targetMutation, enabledOperators)
        reader.accept(visitor, ClassReader.SKIP_FRAMES)
        return writer.toByteArray()
    }

    /**
     * Generates all possible mutants for a class.
     * Returns list of (mutation, mutatedBytecode) pairs.
     */
    fun generateMutants(classBytes: ByteArray): List<Pair<MutationInfo, ByteArray>> {
        val mutations = scanMutations(classBytes)
        return mutations.mapNotNull { mutation ->
            val mutatedBytes = applyMutation(classBytes, mutation)
            if (mutatedBytes.contentEquals(classBytes)) {
                // Mutation was not actually applied — skip to avoid false SURVIVED
                null
            } else {
                mutation to mutatedBytes
            }
        }
    }

    /**
     * Read the internal (slashed) class name from bytecode using a minimal visit.
     * Returns null if parsing fails.
     */
    private fun readInternalName(classBytes: ByteArray): String? {
        var name: String? = null
        return try {
            ClassReader(classBytes).accept(
                object : ClassVisitor(Opcodes.ASM9) {
                    override fun visit(
                        version: Int,
                        access: Int,
                        n: String,
                        signature: String?,
                        superName: String?,
                        interfaces: Array<out String>?,
                    ) {
                        name = n
                    }
                },
                ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
            )
            name
        } catch (e: Throwable) {
            null
        }
    }
}
