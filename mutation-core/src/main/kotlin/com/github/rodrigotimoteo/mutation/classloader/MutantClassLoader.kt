package com.github.rodrigotimoteo.mutation.classloader

import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import com.github.rodrigotimoteo.mutation.mutator.Mutator

/**
 * Custom ClassLoader that applies mutations on-the-fly when loading classes.
 * Each instance is configured with a specific mutation to apply.
 */
class MutantClassLoader(
    parent: ClassLoader,
    private val originalClassBytes: Map<String, ByteArray>,
    private val targetMutation: MutationInfo,
    private val mutator: Mutator,
) : ClassLoader(parent) {
    private val mutatedCache = mutableMapOf<String, Class<*>>()

    override fun findClass(name: String): Class<*> {
        val className = name.replace('.', '/')
        val classBytes =
            originalClassBytes[className]
                ?: throw ClassNotFoundException("Class not found in original classpath: $name")

        // Compute or fetch the mutated bytes
        val mutatedBytes =
            if (className == targetMutation.className.replace('.', '/')) {
                mutator.applyMutation(classBytes, targetMutation)
            } else {
                classBytes
            }

        return defineClass(name, mutatedBytes, 0, mutatedBytes.size)
    }

    override fun loadClass(
        name: String,
        resolve: Boolean,
    ): Class<*> {
        // Cache check: if we've already defined this class, return the cached instance.
        val className = name.replace('.', '/')
        mutatedCache[className]?.let { return it }

        // Check if this is a class we should mutate
        val clazz =
            if (className == targetMutation.className.replace('.', '/')) {
                findClass(name)
            } else {
                super.loadClass(name, resolve)
            }
        mutatedCache[className] = clazz
        return clazz
    }
}

/**
 * Factory for creating MutantClassLoader instances.
 */
object MutantClassLoaderFactory {
    fun create(
        parent: ClassLoader,
        originalClassBytes: Map<String, ByteArray>,
        targetMutation: MutationInfo,
        mutator: Mutator,
    ): MutantClassLoader {
        return MutantClassLoader(parent, originalClassBytes, targetMutation, mutator)
    }
}
