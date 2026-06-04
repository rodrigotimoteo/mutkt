package com.github.rodrigotimoteo.mutation.mutator

import org.objectweb.asm.tree.MethodNode
import java.util.ServiceLoader

/**
 * Interface for custom mutation operators.
 *
 * Users can implement this interface to add their own mutation operators.
 * Custom mutators are discovered via ServiceLoader (SPI pattern).
 */
interface CustomMutator {

    /**
     * Name of the mutation operator.
     */
    val name: String

    /**
     * Description of what this operator does.
     */
    val description: String

    /**
     * Check if this operator can mutate the given method.
     *
     * @param methodNode The method to check
     * @return true if this operator can mutate the method
     */
    fun canMutate(methodNode: MethodNode): Boolean

    /**
     * Generate mutations for the given method.
     *
     * @param methodNode The method to mutate
     * @return List of mutation information
     */
    fun generateMutations(methodNode: MethodNode): List<MutationInfo>

    /**
     * Apply a mutation to the method.
     *
     * @param methodNode The method to mutate
     * @param mutation The mutation to apply
     * @return The mutated method node
     */
    fun applyMutation(methodNode: MethodNode, mutation: MutationInfo): MethodNode
}

/**
 * Registry for custom mutators.
 */
class CustomMutatorRegistry {

    private val mutators = mutableListOf<CustomMutator>()

    /**
     * Register a custom mutator.
     */
    fun register(mutator: CustomMutator) {
        mutators.add(mutator)
    }

    /**
     * Get all registered custom mutators.
     */
    fun getMutators(): List<CustomMutator> {
        return mutators.toList()
    }

    /**
     * Get mutator by name.
     */
    fun getMutator(name: String): CustomMutator? {
        return mutators.find { it.name == name }
    }

    /**
     * Load custom mutators from ServiceLoader.
     */
    fun loadFromServiceLoader(): CustomMutatorRegistry {
        val registry = CustomMutatorRegistry()
        val serviceLoader = ServiceLoader.load(CustomMutator::class.java)
        for (mutator in serviceLoader) {
            registry.register(mutator)
        }
        return registry
    }
}
