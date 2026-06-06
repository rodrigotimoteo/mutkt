package com.github.rodrigotimoteo.mutation.classloader

import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import com.github.rodrigotimoteo.mutation.mutator.Mutator

/**
 * Custom ClassLoader that applies a single mutation on-the-fly when loading classes.
 *
 * Each instance is configured with exactly one [targetMutation] to apply.
 * The classloader loads ALL project classes (those in [originalClassBytes]) through
 * this classloader via [findClass]/[defineClass], so that dependency resolution between
 * project classes goes through this classloader — ensuring test code sees the mutated
 * target class, not the original from parent.
 *
 * External dependencies (JUnit, MockK, JDK, coroutines, etc.) are delegated to the
 * parent classloader. If [defineClass] fails for a project class (e.g. due to missing
 * Kotlin runtime metadata), the classloader falls back to parent gracefully.
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

    override fun loadClass(
        name: String,
        resolve: Boolean,
    ): Class<*> {
        val binaryName = name.replace('/', '.')
        val slashedName = name.replace('.', '/')

        val loaded = findLoadedClass(binaryName)
        if (loaded != null) return loaded

        val classBytes = originalClassBytes[slashedName]
        if (classBytes != null) {
            // Project class — load through this classloader so dependency resolution
            // (when this class references the target class) goes through us.
            try {
                val mutatedBytes =
                    if (slashedName == targetClassName) {
                        mutator.applyMutation(classBytes, targetMutation)
                    } else {
                        classBytes
                    }
                val clazz = defineClass(binaryName, mutatedBytes, 0, mutatedBytes.size)
                if (resolve) {
                    resolveClass(clazz)
                }
                return clazz
            } catch (e: LinkageError) {
                // defineClass failed — fall back to parent for complex Kotlin classes
                // that need runtime metadata not available in raw bytecode.
                // The test will see the original class for this specific case.
            }
        }

        // External dependency or fallback — delegate to parent.
        return super.loadClass(binaryName, resolve)
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
