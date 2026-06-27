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
 * Per-mutation classloader. Loads ONLY the mutated target class (and its
 * inner classes). Test classes are loaded by the parent [TestClassLoader]
 * (defined once per group, shared across all mutants of the same target).
 * Non-target project classes are loaded by the grandparent
 * [BaseProjectClassLoader].
 *
 * 3-tier hierarchy (H-R3-perf-1): previously this loader also re-defineClass'd
 * the same test classes for every mutant of a target, paying a class-verification
 * cost O(M × T) where M = mutations and T = test classes. With the middle tier,
 * test classes are defined once per group (O(T) per target).
 *
 * The parent classloader chain resolves test classes back through this loader
 * when they reference the target — JVM class resolution follows the loader of
 * the referencing class, so test classes (loaded by [TestClassLoader]) that
 * reference the target (e.g. `FooTarget` field type) trigger loading through
 * [TestClassLoader] → [MutationClassLoader], which returns the mutated version.
 */
class MutationClassLoader(
    private val testLoader: TestClassLoader,
    private val targetClassName: String,
    private val targetBytes: ByteArray,
    private val allClassBytes: Map<String, ByteArray>,
    val failedClassCache: MutableSet<String>,
) : ClassLoader(testLoader) {
    /**
     * Convenience constructor for tests and legacy callers that pass a flat
     * [BaseProjectClassLoader] + testClassBytes map. Builds a transient
     * [TestClassLoader] internally so the 3-tier hierarchy is preserved
     * without forcing every call site to wire up the middle tier.
     */
    constructor(
        baseLoader: BaseProjectClassLoader,
        targetClassName: String,
        targetBytes: ByteArray,
        testClassBytes: Map<String, ByteArray>,
        allClassBytes: Map<String, ByteArray>,
        failedClassCache: MutableSet<String>,
    ) : this(
        TestClassLoader(baseLoader, testClassBytes, allClassBytes, failedClassCache),
        targetClassName,
        targetBytes,
        allClassBytes,
        failedClassCache,
    )

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

            // Everything else → TestClassLoader (test classes) → BaseProjectClassLoader
            // (non-target project classes + JDK). The parent chain handles both.
            return super.loadClass(binaryName, resolve)
        }
    }
}

/**
 * Middle tier of the 3-tier hierarchy. Defines test classes ONCE per group,
 * shared across all mutants of the same target class. Sits between
 * [BaseProjectClassLoader] (grandparent, non-target project classes) and
 * [MutationClassLoader] (per-mutant child, target class).
 *
 * Test classes are loaded here rather than per-mutant so that:
 *   1. `defineClass` runs once per test class per run (not once per mutant),
 *      cutting classloader cost from O(M × T) to O(T) per target.
 *   2. The same `Class<?>` identity is returned for a test class across
 *      mutants, which JVM caching requires for class-init idempotency.
 *
 * The chain [MutationClassLoader] → [TestClassLoader] → [BaseProjectClassLoader]
 * means test classes that reference the target class are resolved through
 * the [MutationClassLoader] child — which returns the mutated version.
 */
class TestClassLoader(
    baseLoader: BaseProjectClassLoader,
    private val testClassBytes: Map<String, ByteArray>,
    private val allClassBytes: Map<String, ByteArray>,
    val failedClassCache: MutableSet<String>,
) : ClassLoader(baseLoader) {
    // Top-level test class names (e.g. "com/example/FooTest"). Inner/nested
    // classes are detected by splitting on '$' and checking the prefix
    // against this set — constant-time membership per loadClass call.
    //
    // The previous implementation used a set of `"name$"` prefixes and
    // matched via `startsWith`, which was both O(n) per lookup AND
    // misclassified unrelated classes (e.g. `com/example/foo/BarHelper`
    // matched prefix `com/example/foo/Bar`). This version is O(1) and
    // only matches true test classes and their inner classes.
    private val testClassNames: Set<String> = testClassBytes.keys

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

            // Test class (or inner class of test class) → intercept to keep in same classloader.
            // O(1) membership: exact-name hit OR prefix-before-'$' hit. The '$' split prevents
            // false positives like `com/example/foo/BarHelper` matching `com/example/foo/Bar`.
            val dollarIdx = slashedName.indexOf('$')
            val isTestOrInner =
                slashedName in testClassNames ||
                    (dollarIdx > 0 && slashedName.substring(0, dollarIdx) in testClassNames)
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

            // Non-test, non-target → BaseProjectClassLoader (non-target project classes + JDK).
            return super.loadClass(binaryName, resolve)
        }
    }
}

/**
 * Container for a 3-tier classloader group (H-R3-perf-1).
 *
 * - [base]: defines non-target project classes once per group.
 * - [test]: defines test classes once per group (was previously re-defined
 *   per mutant — see [TestClassLoader] docs for the O(M × T) → O(T) win).
 * - For each mutation, call [MutantClassLoaderFactory.createMutationLoader]
 *   with this group to get a per-mutant loader that defines the mutated
 *   target only.
 */
class MutantClassLoaderGroup(
    val base: BaseProjectClassLoader,
    val test: TestClassLoader,
)

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
 * val loader = MutantClassLoaderFactory.createMutationLoader(group, target, mutatedBytes, classFiles)
 * // ... run tests ...
 * ```
 */
object MutantClassLoaderFactory {
    /**
     * Creates the 3-tier classloader group for a target class:
     * [BaseProjectClassLoader] (non-target project) → [TestClassLoader]
     * (test classes, defined once) → per-mutant [MutationClassLoader]
     * (created by [createMutationLoader]).
     *
     * Test classes and the target (plus target inner classes) are excluded
     * from the base so middle/child tiers can intercept them.
     *
     * Each call creates a fresh, isolated failed-class cache. The cache is
     * shared with the [MutationClassLoader]s and [TestClassLoader] produced
     * for the same group, but never with other groups.
     */
    fun createGroup(
        parent: ClassLoader,
        classFiles: Map<String, ByteArray>,
        testClassBytes: Map<String, ByteArray>,
        targetClassName: String,
    ): MutantClassLoaderGroup {
        val excludedClasses = mutableSetOf(targetClassName)
        // Exclude target inner classes (companion, nested) so child MutationClassLoader
        // can define them alongside the mutated target.
        val targetInnerPrefix = "$targetClassName\$"
        excludedClasses.addAll(classFiles.keys.filter { it.startsWith(targetInnerPrefix) })
        // Also exclude test classes so they're loaded by TestClassLoader.
        excludedClasses.addAll(testClassBytes.keys)
        val failedCache: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val base = BaseProjectClassLoader(parent, classFiles, excludedClasses, failedCache)
        val test = TestClassLoader(base, testClassBytes, classFiles, failedCache)
        return MutantClassLoaderGroup(base, test)
    }

    /**
     * Creates a per-mutant [MutationClassLoader] that defines the mutated
     * target only. Test classes and project classes are resolved through
     * the parent [TestClassLoader] / [BaseProjectClassLoader] in the group.
     * The failed-class cache is shared with the group.
     */
    fun createMutationLoader(
        group: MutantClassLoaderGroup,
        targetClassName: String,
        targetBytes: ByteArray,
        allClassBytes: Map<String, ByteArray>,
    ): MutationClassLoader {
        return MutationClassLoader(
            group.test,
            targetClassName,
            targetBytes,
            allClassBytes,
            group.base.failedClassCache,
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
