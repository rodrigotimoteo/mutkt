package com.github.rodrigotimoteo.mutation.classloader

import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import com.github.rodrigotimoteo.mutation.mutator.Mutator
import java.util.concurrent.ConcurrentHashMap

/**
 * Custom ClassLoader that applies a single mutation on-the-fly when loading classes.
 *
 * Loads ALL project classes (those in [originalClassBytes]) through this classloader
 * via [defineClass], so that dependency resolution between project classes goes through
 * this classloader — ensuring test code sees the mutated target class.
 *
 * External dependencies (JUnit, MockK, JDK, coroutines, etc.) are delegated to the
 * parent classloader. If [defineClass] fails for a project class, it's cached in
 * [failedClassCache] and the class falls back to parent on subsequent requests.
 *
 * @param preMutatedBytes Pre-computed mutated bytes for the target class.
 * @param failedClassCache Shared cache of class names that fail [defineClass].
 */
class MutantClassLoader(
    parent: ClassLoader,
    private val originalClassBytes: Map<String, ByteArray>,
    private val targetMutation: MutationInfo,
    private val mutator: Mutator,
    private val preMutatedBytes: ByteArray? = null,
    private val failedClassCache: MutableSet<String> = ConcurrentHashMap.newKeySet(),
) : ClassLoader(parent) {
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
            // Skip classes that consistently fail defineClass (cached from prior attempts).
            if (slashedName in failedClassCache) {
                return super.loadClass(binaryName, resolve)
            }

            try {
                val mutatedBytes =
                    if (slashedName == targetClassName) {
                        preMutatedBytes ?: mutator.applyMutation(classBytes, targetMutation)
                    } else {
                        classBytes
                    }
                val clazz = defineClass(binaryName, mutatedBytes, 0, mutatedBytes.size)
                if (resolve) resolveClass(clazz)
                return clazz
            } catch (e: LinkageError) {
                failedClassCache.add(slashedName)
            }
        }

        // External dependency or fallback — delegate to parent.
        return super.loadClass(binaryName, resolve)
    }
}

/**
 * Factory for creating [MutantClassLoader] instances.
 *
 * Provides a shared [failedClassCache] across mutations, avoiding repeated
 * LinkageError failures for complex Kotlin classes.
 */
object MutantClassLoaderFactory {
    private val sharedFailedClassCache: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun create(
        parent: ClassLoader,
        originalClassBytes: Map<String, ByteArray>,
        targetMutation: MutationInfo,
        mutator: Mutator,
        preMutatedBytes: ByteArray? = null,
    ): MutantClassLoader {
        return MutantClassLoader(
            parent,
            originalClassBytes,
            targetMutation,
            mutator,
            preMutatedBytes,
            sharedFailedClassCache,
        )
    }

    fun resetCache() {
        sharedFailedClassCache.clear()
    }
}
