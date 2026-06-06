package com.github.rodrigotimoteo.mutation.classloader

import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import com.github.rodrigotimoteo.mutation.mutator.Mutator

/**
 * Custom ClassLoader that applies a single mutation on-the-fly when loading classes.
 *
 * Each instance is configured with exactly one [targetMutation] to apply.
 * The classloader intercepts loading of the target class and returns a mutated version;
 * all other classes are delegated to the parent classloader via standard parent-first
 * delegation, with one exception: project classes (those present in [originalClassBytes])
 * are also loaded through this classloader so that dependency resolution ensures
 * the mutated target class is visible to all project code — not the original from parent.
 *
 * **Note:** The [originalClassBytes] map is consumed as-is — callers must not modify
 * the byte arrays after passing them to this classloader.
 *
 * **Thread safety:** Each MutantClassLoader instance is independent. Mutations run in
 * separate threads with separate classloader instances — no shared mutable state.
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
        val binaryName = name.replace('/', '.')
        val slashedName = name.replace('.', '/')

        val alreadyLoaded = findLoadedClass(binaryName)
        if (alreadyLoaded != null) return alreadyLoaded

        val classBytes =
            originalClassBytes[slashedName]
                ?: throw ClassNotFoundException("Class not found in original classpath: $binaryName")

        val mutatedBytes =
            if (slashedName == targetClassName) {
                mutator.applyMutation(classBytes, targetMutation)
            } else {
                classBytes
            }

        return defineClass(binaryName, mutatedBytes, 0, mutatedBytes.size)
    }

    override fun loadClass(
        name: String,
        resolve: Boolean,
    ): Class<*> {
        val binaryName = name.replace('/', '.')
        val slashedName = name.replace('.', '/')

        val loaded = findLoadedClass(binaryName)
        if (loaded != null) return loaded

        // Always delegate non-target classes to parent.
        // The target class is the only one we intercept.
        if (slashedName != targetClassName) {
            return super.loadClass(binaryName, resolve)
        }

        // Target class: load mutated version through this classloader.
        val clazz = findClass(binaryName)
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
