package com.github.rodrigotimoteo.mutation.classloader

import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import com.github.rodrigotimoteo.mutation.mutator.Mutator
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads non-target project classes. Shared across all mutations of the same target class.
 * Excludes the target class so child [MutationClassLoader] can intercept it.
 *
 * Successful [defineClass] calls are cached by the JVM. LinkageError classes are
 * cached in [failedClassCache] to skip expensive verification on subsequent loads.
 */
class BaseProjectClassLoader(
    parent: ClassLoader,
    private val classBytes: Map<String, ByteArray>,
    private val excludedClasses: Set<String>,
    private val failedClassCache: MutableSet<String>,
) : ClassLoader(parent) {
    override fun loadClass(
        name: String,
        resolve: Boolean,
    ): Class<*> {
        val binaryName = name.replace('/', '.')
        val slashedName = name.replace('.', '/')

        val loaded = findLoadedClass(binaryName)
        if (loaded != null) return loaded

        // Excluded classes (target + test classes) handled by child MutationClassLoader.
        if (slashedName in excludedClasses) {
            return super.loadClass(binaryName, resolve)
        }

        if (slashedName in failedClassCache) {
            return super.loadClass(binaryName, resolve)
        }

        val bytes = classBytes[slashedName]
        if (bytes != null) {
            try {
                val clazz = defineClass(binaryName, bytes, 0, bytes.size)
                if (resolve) resolveClass(clazz)
                return clazz
            } catch (e: LinkageError) {
                failedClassCache.add(slashedName)
            }
        }

        return super.loadClass(binaryName, resolve)
    }
}

/**
 * Per-mutation classloader. Loads the mutated target class and test classes.
 * Delegates non-target project classes to [BaseProjectClassLoader].
 *
 * Test classes are loaded here (not by the base) so that when they reference
 * the target class, JVM resolves it through this classloader — seeing the
 * mutated version.
 */
class MutationClassLoader(
    private val baseLoader: BaseProjectClassLoader,
    private val targetClassName: String,
    private val targetBytes: ByteArray,
    private val testClassBytes: Map<String, ByteArray>,
    private val failedClassCache: MutableSet<String>,
) : ClassLoader(baseLoader) {
    override fun loadClass(
        name: String,
        resolve: Boolean,
    ): Class<*> {
        val binaryName = name.replace('/', '.')
        val slashedName = name.replace('.', '/')

        val loaded = findLoadedClass(binaryName)
        if (loaded != null) return loaded

        // Target class → mutated bytes.
        if (slashedName == targetClassName) {
            try {
                val clazz = defineClass(binaryName, targetBytes, 0, targetBytes.size)
                if (resolve) resolveClass(clazz)
                return clazz
            } catch (e: LinkageError) {
                failedClassCache.add(slashedName)
                return super.loadClass(binaryName, resolve)
            }
        }

        // Test class → define from test bytes.
        val testBytes = testClassBytes[slashedName]
        if (testBytes != null) {
            if (slashedName !in failedClassCache) {
                try {
                    val clazz = defineClass(binaryName, testBytes, 0, testBytes.size)
                    if (resolve) resolveClass(clazz)
                    return clazz
                } catch (e: LinkageError) {
                    failedClassCache.add(slashedName)
                    return super.loadClass(binaryName, resolve)
                }
            }
        }

        // Everything else → BaseProjectClassLoader (non-target project classes + JDK).
        return super.loadClass(binaryName, resolve)
    }
}

/**
 * Factory for creating classloader hierarchies per target class.
 *
 * Usage per mutation run:
 * ```kotlin
 * MutantClassLoaderFactory.resetCache()
 * val group = MutantClassLoaderFactory.createGroup(parent, classFiles, testClassBytes)
 * // For each mutation targeting className:
 * val loader = group.createClassLoader(targetClassName, mutatedTargetBytes)
 * // ... run tests ...
 * group.close()
 * ```
 */
object MutantClassLoaderFactory {
    private val sharedFailedClassCache: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun resetCache() {
        sharedFailedClassCache.clear()
    }

    /**
     * Creates a [BaseProjectClassLoader] for a target class, excluding it
     * (and test classes) from the base so child loaders can intercept them.
     */
    fun createGroup(
        parent: ClassLoader,
        classFiles: Map<String, ByteArray>,
        testClassBytes: Map<String, ByteArray>,
        targetClassName: String,
    ): BaseProjectClassLoader {
        val excludedClasses = mutableSetOf(targetClassName)
        // Also exclude test classes so they're loaded by MutationClassLoader.
        excludedClasses.addAll(testClassBytes.keys)
        return BaseProjectClassLoader(parent, classFiles, excludedClasses, sharedFailedClassCache)
    }

    /**
     * Creates a [MutationClassLoader] that loads the mutated target + test classes,
     * delegating everything else to [baseLoader].
     */
    fun createMutationLoader(
        baseLoader: BaseProjectClassLoader,
        targetClassName: String,
        targetBytes: ByteArray,
        testClassBytes: Map<String, ByteArray>,
    ): MutationClassLoader {
        return MutationClassLoader(
            baseLoader,
            targetClassName,
            targetBytes,
            testClassBytes,
            sharedFailedClassCache,
        )
    }

    /**
     * Legacy API: creates a flat [MutantClassLoader] (for tests and backward compatibility).
     */
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
}

/**
 * Legacy flat classloader (no base/mutation split).
 * Used by existing tests. New code should use [BaseProjectClassLoader] + [MutationClassLoader].
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

        return super.loadClass(binaryName, resolve)
    }
}
