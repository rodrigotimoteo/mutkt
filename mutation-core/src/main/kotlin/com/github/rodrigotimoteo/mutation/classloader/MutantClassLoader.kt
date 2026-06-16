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
 *
 * Concurrency: uses [getClassLoadingLock] (the standard ClassLoader per-name
 * interned lock) instead of a custom striped lock array. The interned lock
 * preserves the JVM's classloader deadlock guarantee: if thread T1 holds the
 * lock for class A and triggers loading of class B (held by thread T2), the
 * JVM detects the cycle and resolves it correctly. Custom stripes break this
 * guarantee because two threads loading the same class can take different
 * stripes depending on hash collisions, allowing partial-order deadlocks.
 */
class BaseProjectClassLoader(
    parent: ClassLoader,
    private val classBytes: Map<String, ByteArray>,
    private val excludedClasses: Set<String>,
    val failedClassCache: MutableSet<String>,
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
            throw ClassNotFoundException(name)
        }

        if (slashedName in failedClassCache) {
            return super.loadClass(binaryName, resolve)
        }

        // Serialize concurrent defineClass attempts for the same class name to avoid
        // duplicate class definition LinkageError under concurrent loadClass calls.
        synchronized(getClassLoadingLock(binaryName)) {
            findLoadedClass(binaryName)?.let { return it }

            val bytes = classBytes[slashedName]
            if (bytes != null) {
                try {
                    val clazz = defineClass(binaryName, bytes, 0, bytes.size)
                    if (resolve) resolveClass(clazz)
                    return clazz
                } catch (e: LinkageError) {
                    failedClassCache.add(slashedName)
                    throw e
                }
            }

            return super.loadClass(binaryName, resolve)
        }
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
    private val allClassBytes: Map<String, ByteArray>,
    val failedClassCache: MutableSet<String>,
) : ClassLoader(baseLoader) {
    override fun loadClass(
        name: String,
        resolve: Boolean,
    ): Class<*> {
        val binaryName = name.replace('/', '.')
        val slashedName = name.replace('.', '/')

        val loaded = findLoadedClass(binaryName)
        if (loaded != null) return loaded

        // Serialize concurrent defineClass attempts for the same class name to avoid
        // duplicate class definition LinkageError under concurrent loadClass calls.
        synchronized(getClassLoadingLock(binaryName)) {
            // Re-check after acquiring lock — another thread may have defined it.
            findLoadedClass(binaryName)?.let { return it }

            // Target class → mutated bytes.
            if (slashedName == targetClassName) {
                try {
                    val clazz = defineClass(binaryName, targetBytes, 0, targetBytes.size)
                    if (resolve) resolveClass(clazz)
                    return clazz
                } catch (e: LinkageError) {
                    failedClassCache.add(slashedName)
                    throw e
                }
            }

            // Target inner class (companion, nested) → define from project bytes in
            // THIS classloader so they share runtime identity with the mutated target.
            if (slashedName.startsWith("$targetClassName\$") && slashedName !in failedClassCache) {
                val innerBytes = allClassBytes[slashedName]
                if (innerBytes != null) {
                    try {
                        val clazz = defineClass(binaryName, innerBytes, 0, innerBytes.size)
                        if (resolve) resolveClass(clazz)
                        return clazz
                    } catch (e: LinkageError) {
                        failedClassCache.add(slashedName)
                        return super.loadClass(binaryName, resolve)
                    }
                }
            }

            // Test class (or inner class of test class) → intercept to keep in same classloader.
            val isTestOrInner =
                testClassBytes.keys.any { testName ->
                    slashedName == testName || slashedName.startsWith("$testName$")
                }
            if (isTestOrInner && slashedName !in failedClassCache) {
                val testBytes = testClassBytes[slashedName]
                if (testBytes != null) {
                    // Top-level test class → define from test bytes.
                    try {
                        val clazz = defineClass(binaryName, testBytes, 0, testBytes.size)
                        if (resolve) resolveClass(clazz)
                        return clazz
                    } catch (e: LinkageError) {
                        failedClassCache.add(slashedName)
                        return super.loadClass(binaryName, resolve)
                    }
                }
                // Inner class of test class → define from project bytes in THIS classloader
                // to avoid cross-module IllegalAccessError.
                val innerBytes = allClassBytes[slashedName]
                if (innerBytes != null) {
                    try {
                        val clazz = defineClass(binaryName, innerBytes, 0, innerBytes.size)
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
}

/**
 * Factory for creating classloader hierarchies per target class.
 *
 * The failed-class cache is created fresh per [createGroup] call (per
 * [BaseProjectClassLoader] instance). Concurrent engine runs no longer race
 * on a shared singleton set — each group owns its own cache and failures in
 * one group cannot poison another.
 *
 * Usage per mutation run:
 * ```kotlin
 * val group = MutantClassLoaderFactory.createGroup(parent, classFiles, testClassBytes, target)
 * // For each mutation targeting className:
 * val loader = MutantClassLoaderFactory.createMutationLoader(group, target, mutatedBytes, ...)
 * // ... run tests ...
 * ```
 */
object MutantClassLoaderFactory {
    /**
     * No-op retained for backward compatibility. Caches are now per-group;
     * there is no singleton state to clear. New code should not depend on it.
     */
    @Deprecated(
        "Failed-class cache is now scoped per BaseProjectClassLoader instance. " +
            "This method is a no-op and will be removed in a future release.",
        ReplaceWith("Unit"),
    )
    fun resetCache() {
        // intentional no-op
    }

    /**
     * Creates a [BaseProjectClassLoader] for a target class, excluding it
     * (and test classes) from the base so child loaders can intercept them.
     *
     * Each call creates a fresh, isolated failed-class cache. The cache is
     * shared with the [MutationClassLoader]s produced by [createMutationLoader]
     * for the same group, but never with other groups.
     */
    fun createGroup(
        parent: ClassLoader,
        classFiles: Map<String, ByteArray>,
        testClassBytes: Map<String, ByteArray>,
        targetClassName: String,
    ): BaseProjectClassLoader {
        val excludedClasses = mutableSetOf(targetClassName)
        // Exclude target inner classes (companion, nested) so child MutationClassLoader
        // can define them alongside the mutated target.
        val targetInnerPrefix = "$targetClassName\$"
        excludedClasses.addAll(classFiles.keys.filter { it.startsWith(targetInnerPrefix) })
        // Also exclude test classes so they're loaded by MutationClassLoader.
        excludedClasses.addAll(testClassBytes.keys)
        val failedCache: MutableSet<String> = ConcurrentHashMap.newKeySet()
        return BaseProjectClassLoader(parent, classFiles, excludedClasses, failedCache)
    }

    /**
     * Creates a [MutationClassLoader] that loads the mutated target + test classes,
     * delegating everything else to [baseLoader]. The failed-class cache is
     * inherited from [baseLoader] so both loaders in the same group see the
     * same LinkageError cache.
     */
    fun createMutationLoader(
        baseLoader: BaseProjectClassLoader,
        targetClassName: String,
        targetBytes: ByteArray,
        testClassBytes: Map<String, ByteArray>,
        allClassBytes: Map<String, ByteArray>,
    ): MutationClassLoader {
        return MutationClassLoader(
            baseLoader,
            targetClassName,
            targetBytes,
            testClassBytes,
            allClassBytes,
            baseLoader.failedClassCache,
        )
    }

    /**
     * Legacy API: creates a flat [MutantClassLoader] (for tests and backward compatibility).
     * The failed-class cache is created fresh per call.
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

        // Serialize concurrent defineClass attempts for the same class name to avoid
        // duplicate class definition LinkageError under concurrent loadClass calls.
        synchronized(getClassLoadingLock(binaryName)) {
            findLoadedClass(binaryName)?.let { return it }

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
                    if (slashedName == targetClassName) throw e
                }
            }

            return super.loadClass(binaryName, resolve)
        }
    }
}
