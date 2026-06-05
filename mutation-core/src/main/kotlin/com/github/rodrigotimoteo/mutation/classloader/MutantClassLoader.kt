package com.github.rodrigotimoteo.mutation.classloader

import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import com.github.rodrigotimoteo.mutation.mutator.Mutator

/**
 * Custom ClassLoader that applies a single mutation on-the-fly when loading the target class.
 *
 * Each instance is configured with exactly one [targetMutation] to apply.
 * The classloader intercepts loading of the target class and returns a mutated version;
 * all other classes are delegated to the parent classloader via standard parent-first delegation.
 *
 * **Note:** The [originalClassBytes] map is consumed as-is — callers must not modify
 * the byte arrays after passing them to this classloader.
 *
 * **Thread safety:** This classloader is designed for single-threaded mutation testing runs.
 * Concurrent `loadClass` calls for the same target class may trigger [LinkageError]
 * from duplicate `defineClass` calls.
 */
class MutantClassLoader(
    parent: ClassLoader,
    private val originalClassBytes: Map<String, ByteArray>,
    private val targetMutation: MutationInfo,
    private val mutator: Mutator,
) : ClassLoader(parent) {
    /** Slashed internal form of the target mutation's class name, computed once. */
    private val targetClassName: String = targetMutation.className.replace('.', '/')

    override fun findClass(name: String): Class<*> {
        // Prevent duplicate defineClass if called directly (e.g. via reflection).
        val alreadyLoaded = findLoadedClass(name)
        if (alreadyLoaded != null) return alreadyLoaded

        val className = name.replace('.', '/')
        val classBytes =
            originalClassBytes[className]
                ?: throw ClassNotFoundException("Class not found in original classpath: $name")

        // Apply mutation only if this is the target class.
        val mutatedBytes =
            if (className == targetClassName) {
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
        // Use JVM's built-in loaded-class tracking instead of a redundant cache.
        val loaded = findLoadedClass(name)
        if (loaded != null) return loaded

        val className = name.replace('.', '/')
        val clazz =
            if (className == targetClassName) {
                findClass(name)
            } else {
                // Delegate to parent with standard parent-first delegation.
                super.loadClass(name, false)
            }

        // Link the class if requested — required for target classes loaded via findClass.
        if (resolve) {
            resolveClass(clazz)
        }

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
