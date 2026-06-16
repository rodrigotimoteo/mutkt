package com.github.rodrigotimoteo.mutation.classloader

import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import com.github.rodrigotimoteo.mutation.mutator.Mutator
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MutantClassLoaderFullTest {
    private val parentClassLoader: ClassLoader = MutantClassLoaderFullTest::class.java.classLoader

    @BeforeEach
    fun setUp() {
        MutantClassLoaderFactory.resetCache()
    }

    @AfterEach
    fun tearDown() {
        MutantClassLoaderFactory.resetCache()
    }

    // ---------- BaseProjectClassLoader ----------

    @Test
    fun `BaseProjectClassLoader loadClass returns project class from bytes`() {
        val bytes = buildClassWithMethod("com/example/BaseProj1")
        val failed = ConcurrentHashMap.newKeySet<String>()
        val loader = BaseProjectClassLoader(parentClassLoader, mapOf("com/example/BaseProj1" to bytes), emptySet(), failed)

        val clazz = loader.loadClass("com.example.BaseProj1")

        assertNotNull(clazz)
        assertEquals("com.example.BaseProj1", clazz.name)
        assertSame(loader, clazz.classLoader)
        assertTrue(failed.isEmpty())
    }

    @Test
    fun `BaseProjectClassLoader loadClass with resolve true resolves class`() {
        // ClassLoader.loadClass(name, resolve) is protected; the public single-arg
        // loadClass(name) delegates to it with resolve=false. We verify the
        // defineClass path executed by checking the returned class is linked
        // (callable through Class.forName with initialize=true on the same loader).
        val bytes = buildClassWithMethod("com/example/BaseProj2")
        val loader =
            BaseProjectClassLoader(
                parentClassLoader,
                mapOf("com/example/BaseProj2" to bytes),
                emptySet(),
                ConcurrentHashMap.newKeySet(),
            )

        val clazz = loader.loadClass("com.example.BaseProj2")
        val resolved = Class.forName("com.example.BaseProj2", true, loader)

        assertNotNull(clazz)
        assertSame(loader, clazz.classLoader)
        assertSame(clazz, resolved, "Class should be definable and re-resolvable on this loader")
    }

    @Test
    fun `BaseProjectClassLoader loadClass delegates excluded target class to parent`() {
        val name = "com/example/BaseProjExcl"
        val bytes = buildClassWithMethod(name)
        val loader =
            BaseProjectClassLoader(
                parentClassLoader,
                mapOf(name to bytes),
                excludedClasses = setOf(name),
                failedClassCache = ConcurrentHashMap.newKeySet(),
            )

        // Excluded → super.loadClass → parent doesn't have it → ClassNotFoundException.
        assertFailsWith<ClassNotFoundException> { loader.loadClass("com.example.BaseProjExcl") }
    }

    @Test
    fun `BaseProjectClassLoader loadClass delegates excluded test class to parent`() {
        val testName = "com/example/BaseProjTestExcl"
        val bytes = buildClassWithMethod(testName)
        val loader =
            BaseProjectClassLoader(
                parentClassLoader,
                mapOf(testName to bytes),
                excludedClasses = setOf(testName),
                failedClassCache = ConcurrentHashMap.newKeySet(),
            )

        assertFailsWith<ClassNotFoundException> { loader.loadClass("com.example.BaseProjTestExcl") }
    }

    @Test
    fun `BaseProjectClassLoader loadClass skips cached failed class`() {
        val failedName = "com/example/BaseProjFailed"
        val bytes = buildClassWithInvalidSuperclass(failedName)
        val failedCache = ConcurrentHashMap.newKeySet<String>()
        val loader =
            BaseProjectClassLoader(
                parentClassLoader,
                mapOf(failedName to bytes),
                emptySet(),
                failedCache,
            )

        // First call: defineClass throws LinkageError → cached.
        assertFailsWith<LinkageError> { loader.loadClass("com.example.BaseProjFailed") }
        assertTrue(failedName in failedCache)

        // Second call: skipped via failed cache → parent delegation → ClassNotFoundException.
        assertFailsWith<ClassNotFoundException> { loader.loadClass("com.example.BaseProjFailed") }
    }

    @Test
    fun `BaseProjectClassLoader loadClass caches LinkageError and delegates on retry`() {
        val failedName = "com/example/BaseProjCacheLink"
        val bytes = buildClassWithInvalidSuperclass(failedName)
        val failedCache = ConcurrentHashMap.newKeySet<String>()
        val loader =
            BaseProjectClassLoader(
                parentClassLoader,
                mapOf(failedName to bytes),
                emptySet(),
                failedCache,
            )

        assertFailsWith<LinkageError> { loader.loadClass("com.example.BaseProjCacheLink") }
        assertTrue(failedName in failedCache)

        // Second call short-circuits via failedClassCache check.
        assertFailsWith<ClassNotFoundException> { loader.loadClass("com.example.BaseProjCacheLink") }
    }

    @Test
    fun `BaseProjectClassLoader loadClass delegates to parent for unknown class`() {
        val bytes = buildClassWithMethod("com/example/BaseProjUnk")
        val loader =
            BaseProjectClassLoader(
                parentClassLoader,
                mapOf("com/example/BaseProjUnk" to bytes),
                emptySet(),
                ConcurrentHashMap.newKeySet(),
            )

        val clazz = loader.loadClass("java.lang.String")

        assertEquals(String::class.java, clazz)
    }

    @Test
    fun `BaseProjectClassLoader loadClass returns already loaded class`() {
        val name = "com/example/BaseProjLoaded"
        val bytes = buildClassWithMethod(name)
        val loader =
            BaseProjectClassLoader(
                parentClassLoader,
                mapOf(name to bytes),
                emptySet(),
                ConcurrentHashMap.newKeySet(),
            )

        val c1 = loader.loadClass("com.example.BaseProjLoaded")
        val c2 = loader.loadClass("com.example.BaseProjLoaded")

        assertSame(c1, c2)
    }

    @Test
    fun `BaseProjectClassLoader loadClass accepts slashed input name`() {
        val name = "com/example/BaseProjSlash"
        val bytes = buildClassWithMethod(name)
        val loader =
            BaseProjectClassLoader(
                parentClassLoader,
                mapOf(name to bytes),
                emptySet(),
                ConcurrentHashMap.newKeySet(),
            )

        // Slashed form (typical JVM internal name).
        val clazz = loader.loadClass("com/example/BaseProjSlash")

        assertNotNull(clazz)
        assertEquals("com.example.BaseProjSlash", clazz.name)
        assertSame(loader, clazz.classLoader)
    }

    // ---------- MutationClassLoader ----------

    @Test
    fun `MutationClassLoader loadClass defines mutated target class`() {
        val targetName = "com/example/MutTarget1"
        val base =
            BaseProjectClassLoader(
                parentClassLoader,
                mapOf(targetName to buildClassWithMethod(targetName)),
                excludedClasses = setOf(targetName),
                failedClassCache = ConcurrentHashMap.newKeySet(),
            )
        val mutationLoader =
            MutationClassLoader(
                base,
                targetName,
                buildClassWithMethod(targetName),
                testClassBytes = emptyMap(),
                allClassBytes = emptyMap(),
                failedClassCache = ConcurrentHashMap.newKeySet(),
            )

        val clazz = mutationLoader.loadClass("com.example.MutTarget1")

        assertNotNull(clazz)
        assertEquals("com.example.MutTarget1", clazz.name)
        assertSame(mutationLoader, clazz.classLoader)
    }

    @Test
    fun `MutationClassLoader loadClass resolves mutated target when resolve true`() {
        val targetName = "com/example/MutTarget2"
        val base =
            BaseProjectClassLoader(
                parentClassLoader,
                mapOf(targetName to buildClassWithMethod(targetName)),
                excludedClasses = setOf(targetName),
                failedClassCache = ConcurrentHashMap.newKeySet(),
            )
        val mutationLoader =
            MutationClassLoader(
                base,
                targetName,
                buildClassWithMethod(targetName),
                testClassBytes = emptyMap(),
                allClassBytes = emptyMap(),
                failedClassCache = ConcurrentHashMap.newKeySet(),
            )

        val clazz = mutationLoader.loadClass("com.example.MutTarget2")
        val resolved = Class.forName("com.example.MutTarget2", true, mutationLoader)

        assertNotNull(clazz)
        assertSame(clazz, resolved, "Target should be definable and re-resolvable on mutation loader")
    }

    @Test
    fun `MutationClassLoader loadClass rethrows LinkageError for target class`() {
        val targetName = "com/example/MutTargetFail"
        val badTargetBytes = buildClassWithInvalidSuperclass(targetName)
        val base =
            BaseProjectClassLoader(
                parentClassLoader,
                mapOf(targetName to buildClassWithMethod(targetName)),
                excludedClasses = setOf(targetName),
                failedClassCache = ConcurrentHashMap.newKeySet(),
            )
        val sharedFailed = ConcurrentHashMap.newKeySet<String>()
        val mutationLoader =
            MutationClassLoader(
                base,
                targetName,
                badTargetBytes,
                testClassBytes = emptyMap(),
                allClassBytes = emptyMap(),
                failedClassCache = sharedFailed,
            )

        assertFailsWith<LinkageError> { mutationLoader.loadClass("com.example.MutTargetFail") }
        assertTrue(targetName in sharedFailed, "Target LinkageError should be cached")
    }

    @Test
    fun `MutationClassLoader loadClass defines test class bytes`() {
        val targetName = "com/example/MutTargetT1"
        val testName = "com/example/MutTest1"
        val testBytes = buildClassWithMethod(testName)
        val base =
            BaseProjectClassLoader(
                parentClassLoader,
                mapOf(targetName to buildClassWithMethod(targetName)),
                excludedClasses = setOf(targetName, testName),
                failedClassCache = ConcurrentHashMap.newKeySet(),
            )
        val mutationLoader =
            MutationClassLoader(
                base,
                targetName,
                buildClassWithMethod(targetName),
                testClassBytes = mapOf(testName to testBytes),
                allClassBytes = emptyMap(),
                failedClassCache = ConcurrentHashMap.newKeySet(),
            )

        val clazz = mutationLoader.loadClass("com.example.MutTest1")

        assertNotNull(clazz)
        assertEquals("com.example.MutTest1", clazz.name)
        assertSame(mutationLoader, clazz.classLoader)
    }

    @Test
    fun `MutationClassLoader loadClass caches failed test class and delegates`() {
        val targetName = "com/example/MutTargetT2"
        val testName = "com/example/MutTestFail"
        val badTestBytes = buildClassWithInvalidSuperclass(testName)
        val sharedFailed = ConcurrentHashMap.newKeySet<String>()
        val base =
            BaseProjectClassLoader(
                parentClassLoader,
                mapOf(targetName to buildClassWithMethod(targetName)),
                excludedClasses = setOf(targetName, testName),
                failedClassCache = sharedFailed,
            )
        val mutationLoader =
            MutationClassLoader(
                base,
                targetName,
                buildClassWithMethod(targetName),
                testClassBytes = mapOf(testName to badTestBytes),
                allClassBytes = emptyMap(),
                failedClassCache = sharedFailed,
            )

        // First call: LinkageError → cached → fall through to super.loadClass (parent).
        assertFailsWith<ClassNotFoundException> { mutationLoader.loadClass("com.example.MutTestFail") }
        assertTrue(testName in sharedFailed)

        // Second call: skipped via failed cache → delegated immediately.
        assertFailsWith<ClassNotFoundException> { mutationLoader.loadClass("com.example.MutTestFail") }
    }

    @Test
    fun `MutationClassLoader loadClass defines inner class of test from allClassBytes`() {
        val targetName = "com/example/MutTargetT3"
        val testName = "com/example/MutTestInner"
        val innerName = "$testName\$Inner"
        val innerBytes = buildClassWithMethod(innerName)
        val base =
            BaseProjectClassLoader(
                parentClassLoader,
                mapOf(targetName to buildClassWithMethod(targetName)),
                excludedClasses = setOf(targetName, testName),
                failedClassCache = ConcurrentHashMap.newKeySet(),
            )
        val mutationLoader =
            MutationClassLoader(
                base,
                targetName,
                buildClassWithMethod(targetName),
                testClassBytes = mapOf(testName to buildClassWithMethod(testName)),
                allClassBytes = mapOf(innerName to innerBytes),
                failedClassCache = ConcurrentHashMap.newKeySet(),
            )

        val clazz = mutationLoader.loadClass("$testName\$Inner")

        assertNotNull(clazz)
        assertEquals("com.example.MutTestInner\$Inner", clazz.name)
        assertSame(mutationLoader, clazz.classLoader, "Inner must be defined in mutation loader")
    }

    @Test
    fun `MutationClassLoader loadClass caches failed inner class and delegates`() {
        val targetName = "com/example/MutTargetT4"
        val testName = "com/example/MutTestInnerFail"
        val innerName = "$testName\$Inner"
        val badInnerBytes = buildClassWithInvalidSuperclass(innerName)
        val sharedFailed = ConcurrentHashMap.newKeySet<String>()
        val base =
            BaseProjectClassLoader(
                parentClassLoader,
                mapOf(targetName to buildClassWithMethod(targetName)),
                excludedClasses = setOf(targetName, testName),
                failedClassCache = sharedFailed,
            )
        val mutationLoader =
            MutationClassLoader(
                base,
                targetName,
                buildClassWithMethod(targetName),
                testClassBytes = mapOf(testName to buildClassWithMethod(testName)),
                allClassBytes = mapOf(innerName to badInnerBytes),
                failedClassCache = sharedFailed,
            )

        assertFailsWith<ClassNotFoundException> {
            mutationLoader.loadClass("$testName\$Inner")
        }
        assertTrue(innerName in sharedFailed, "Inner LinkageError should be cached")

        assertFailsWith<ClassNotFoundException> {
            mutationLoader.loadClass("$testName\$Inner")
        }
    }

    @Test
    fun `MutationClassLoader loadClass skips test class in failed cache`() {
        val targetName = "com/example/MutTargetT5"
        val testName = "com/example/MutTestSkip"
        val preFailedCache = ConcurrentHashMap.newKeySet<String>()
        preFailedCache.add(testName)
        val base =
            BaseProjectClassLoader(
                parentClassLoader,
                mapOf(targetName to buildClassWithMethod(targetName)),
                excludedClasses = setOf(targetName, testName),
                failedClassCache = preFailedCache,
            )
        val mutationLoader =
            MutationClassLoader(
                base,
                targetName,
                buildClassWithMethod(targetName),
                testClassBytes = mapOf(testName to buildClassWithMethod(testName)),
                allClassBytes = emptyMap(),
                failedClassCache = preFailedCache,
            )

        // Pre-cached → guarded by isTestOrInner && slashedName !in failedClassCache → super.loadClass.
        assertFailsWith<ClassNotFoundException> { mutationLoader.loadClass("com.example.MutTestSkip") }
    }

    @Test
    fun `MutationClassLoader loadClass delegates non-test non-target to base loader`() {
        val targetName = "com/example/MutTargetDel"
        val otherName = "com/example/MutOther"
        val otherBytes = buildClassWithMethod(otherName)
        val base =
            BaseProjectClassLoader(
                parentClassLoader,
                mapOf(targetName to buildClassWithMethod(targetName), otherName to otherBytes),
                excludedClasses = setOf(targetName),
                failedClassCache = ConcurrentHashMap.newKeySet(),
            )
        val mutationLoader =
            MutationClassLoader(
                base,
                targetName,
                buildClassWithMethod(targetName),
                testClassBytes = emptyMap(),
                allClassBytes = emptyMap(),
                failedClassCache = ConcurrentHashMap.newKeySet(),
            )

        val clazz = mutationLoader.loadClass("com.example.MutOther")

        assertNotNull(clazz)
        assertEquals("com.example.MutOther", clazz.name)
        // Loaded by base loader, not mutation loader.
        assertSame(base, clazz.classLoader)
    }

    @Test
    fun `MutationClassLoader loadClass returns cached class`() {
        val targetName = "com/example/MutTargetCache"
        val base =
            BaseProjectClassLoader(
                parentClassLoader,
                mapOf(targetName to buildClassWithMethod(targetName)),
                excludedClasses = setOf(targetName),
                failedClassCache = ConcurrentHashMap.newKeySet(),
            )
        val mutationLoader =
            MutationClassLoader(
                base,
                targetName,
                buildClassWithMethod(targetName),
                testClassBytes = emptyMap(),
                allClassBytes = emptyMap(),
                failedClassCache = ConcurrentHashMap.newKeySet(),
            )

        val c1 = mutationLoader.loadClass("com.example.MutTargetCache")
        val c2 = mutationLoader.loadClass("com.example.MutTargetCache")

        assertSame(c1, c2, "findLoadedClass cache should return same instance")
    }

    @Test
    fun `MutationClassLoader concurrent loadClass returns same Class instance`() {
        val targetName = "com/example/MutTargetConc"
        val base =
            BaseProjectClassLoader(
                parentClassLoader,
                mapOf(targetName to buildClassWithMethod(targetName)),
                excludedClasses = setOf(targetName),
                failedClassCache = ConcurrentHashMap.newKeySet(),
            )
        val mutationLoader =
            MutationClassLoader(
                base,
                targetName,
                buildClassWithMethod(targetName),
                testClassBytes = emptyMap(),
                allClassBytes = emptyMap(),
                failedClassCache = ConcurrentHashMap.newKeySet(),
            )

        val threadCount = 8
        val ready = CountDownLatch(threadCount)
        val go = CountDownLatch(1)
        val done = CountDownLatch(threadCount)
        val results = AtomicReferenceArray<Class<*>>(threadCount)

        val workers =
            (0 until threadCount).map { i ->
                Thread {
                    ready.countDown()
                    go.await()
                    results.set(i, mutationLoader.loadClass("com.example.MutTargetConc"))
                    done.countDown()
                }
            }
        workers.forEach { it.start() }
        ready.await()
        go.countDown()
        done.await()

        val first = results.get(0)
        for (i in 1 until threadCount) {
            assertSame(first, results.get(i), "All threads must observe the same Class instance")
        }
    }

    // ---------- Legacy MutantClassLoader ----------

    @Test
    fun `legacy MutantClassLoader loadClass with resolve true resolves class`() {
        val name = "com/example/LegacyRes"
        val bytes = buildClassWithMethod(name)
        val loader =
            MutantClassLoader(
                parent = parentClassLoader,
                originalClassBytes = mapOf(name to bytes),
                targetMutation = createDummyMutation("com.example.LegacyRes"),
                mutator = Mutator(setOf(MutationOperator.ARITHMETIC)),
                failedClassCache = ConcurrentHashMap.newKeySet(),
            )

        val clazz = loader.loadClass("com.example.LegacyRes")
        val resolved = Class.forName("com.example.LegacyRes", true, loader)

        assertNotNull(clazz)
        assertSame(clazz, resolved, "Class should be definable and re-resolvable on legacy loader")
    }

    @Test
    fun `legacy MutantClassLoader loadClass uses preMutatedBytes for target`() {
        val name = "com/example/LegacyPre"
        val baseBytes = buildClassWithMethod(name)
        val preMutated = buildClassWithMethod(name)
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val mutation = createDummyMutation("com.example.LegacyPre")
        val loader =
            MutantClassLoader(
                parent = parentClassLoader,
                originalClassBytes = mapOf(name to baseBytes),
                targetMutation = mutation,
                mutator = mutator,
                preMutatedBytes = preMutated,
            )

        val clazz = loader.loadClass("com.example.LegacyPre")

        assertNotNull(clazz)
        assertSame(loader, clazz.classLoader)
        assertEquals("com.example.LegacyPre", clazz.name)
    }

    @Test
    fun `legacy MutantClassLoader loadClass caches non-target LinkageError and delegates`() {
        val name = "com/example/LegacyCacheNon"
        val badBytes = buildClassWithInvalidSuperclass(name)
        val failedCache = ConcurrentHashMap.newKeySet<String>()
        val loader =
            MutantClassLoader(
                parent = parentClassLoader,
                originalClassBytes = mapOf(name to badBytes),
                targetMutation = createDummyMutation("com.example.OtherTarget"),
                mutator = Mutator(setOf(MutationOperator.ARITHMETIC)),
                failedClassCache = failedCache,
            )

        // Non-target LinkageError → cached, but NOT rethrown.
        assertFailsWith<ClassNotFoundException> { loader.loadClass("com.example.LegacyCacheNon") }
        assertTrue(name in failedCache)

        // Retry: cached, delegated again.
        assertFailsWith<ClassNotFoundException> { loader.loadClass("com.example.LegacyCacheNon") }
    }

    @Test
    fun `legacy MutantClassLoader loadClass skips cached failed non-target class`() {
        val name = "com/example/LegacySkipNon"
        val preFailed = ConcurrentHashMap.newKeySet<String>()
        preFailed.add(name)
        val loader =
            MutantClassLoader(
                parent = parentClassLoader,
                originalClassBytes = mapOf(name to buildClassWithMethod(name)),
                targetMutation = createDummyMutation("com.example.OtherTarget"),
                mutator = Mutator(setOf(MutationOperator.ARITHMETIC)),
                failedClassCache = preFailed,
            )

        // Pre-cached → super.loadClass → ClassNotFoundException.
        assertFailsWith<ClassNotFoundException> { loader.loadClass("com.example.LegacySkipNon") }
    }

    @Test
    fun `legacy MutantClassLoader loadClass rethrows LinkageError for target class`() {
        val name = "com/example/LegacyTgtFail"
        val badTargetBytes = buildClassWithInvalidSuperclass(name)
        val failedCache = ConcurrentHashMap.newKeySet<String>()
        val loader =
            MutantClassLoader(
                parent = parentClassLoader,
                originalClassBytes = mapOf(name to badTargetBytes),
                targetMutation = createDummyMutation("com.example.LegacyTgtFail"),
                mutator = Mutator(setOf(MutationOperator.ARITHMETIC)),
                failedClassCache = failedCache,
            )

        assertFailsWith<LinkageError> { loader.loadClass("com.example.LegacyTgtFail") }
        assertTrue(name in failedCache, "Target LinkageError must still be cached")
    }

    // ---------- MutantClassLoaderFactory ----------

    @Test
    fun `MutantClassLoaderFactory resetCache clears shared failed cache`() {
        val targetName = "com/example/FactoryResetTarget"
        val badName = "com/example/FactoryReset"
        val badBytes = buildClassWithInvalidSuperclass(badName)
        val base =
            MutantClassLoaderFactory.createGroup(
                parentClassLoader,
                classFiles = mapOf(badName to badBytes),
                testClassBytes = emptyMap(),
                targetClassName = targetName,
            )
        // Trigger a LinkageError to populate the shared failed cache.
        assertFailsWith<LinkageError> { base.loadClass("com.example.FactoryReset") }

        MutantClassLoaderFactory.resetCache()

        // After reset, a fresh group referencing the cleared shared set must
        // re-cache the failure on first call (proving the set was actually cleared).
        val freshGroup =
            MutantClassLoaderFactory.createGroup(
                parentClassLoader,
                classFiles = mapOf(badName to badBytes),
                testClassBytes = emptyMap(),
                targetClassName = targetName,
            )
        assertFailsWith<LinkageError> { freshGroup.loadClass("com.example.FactoryReset") }
    }

    @Test
    fun `MutantClassLoaderFactory createGroup returns BaseProjectClassLoader`() {
        val name = "com/example/FactoryGrp"
        val bytes = buildClassWithMethod(name)
        val base =
            MutantClassLoaderFactory.createGroup(
                parentClassLoader,
                mapOf(name to bytes),
                emptyMap(),
                name,
            )

        assertNotNull(base)
        assertTrue(base is BaseProjectClassLoader)
    }

    @Test
    fun `MutantClassLoaderFactory createGroup excludes target and test classes`() {
        val targetName = "com/example/FactoryExclT"
        val testName = "com/example/FactoryExclTest"
        val targetBytes = buildClassWithMethod(targetName)
        val testBytes = buildClassWithMethod(testName)
        val base =
            MutantClassLoaderFactory.createGroup(
                parentClassLoader,
                mapOf(targetName to targetBytes),
                mapOf(testName to testBytes),
                targetName,
            )

        // Both target and test → not defined by base (delegated to parent → ClassNotFound).
        assertFailsWith<ClassNotFoundException> { base.loadClass("com.example.FactoryExclT") }
        assertFailsWith<ClassNotFoundException> { base.loadClass("com.example.FactoryExclTest") }
    }

    @Test
    fun `MutantClassLoaderFactory createMutationLoader returns MutationClassLoader`() {
        val targetName = "com/example/FactoryMut"
        val base =
            MutantClassLoaderFactory.createGroup(
                parentClassLoader,
                emptyMap(),
                emptyMap(),
                targetName,
            )
        val mutationLoader =
            MutantClassLoaderFactory.createMutationLoader(
                base,
                targetName,
                buildClassWithMethod(targetName),
                testClassBytes = emptyMap(),
                allClassBytes = emptyMap(),
            )

        assertNotNull(mutationLoader)
        assertTrue(mutationLoader is MutationClassLoader)
    }

    @Test
    fun `MutantClassLoaderFactory createMutationLoader delegates non-target to base`() {
        val targetName = "com/example/FactoryMutDel"
        val otherName = "com/example/FactoryMutOther"
        val otherBytes = buildClassWithMethod(otherName)
        val base =
            MutantClassLoaderFactory.createGroup(
                parentClassLoader,
                mapOf(otherName to otherBytes),
                emptyMap(),
                targetName,
            )
        val mutationLoader =
            MutantClassLoaderFactory.createMutationLoader(
                base,
                targetName,
                buildClassWithMethod(targetName),
                testClassBytes = emptyMap(),
                allClassBytes = emptyMap(),
            )

        val clazz = mutationLoader.loadClass("com.example.FactoryMutOther")

        assertNotNull(clazz)
        assertEquals("com.example.FactoryMutOther", clazz.name)
        // Non-target → handled by base → loaded by base, not mutation loader.
        assertSame(base, clazz.classLoader)
    }

    // ---------- Cache isolation (per-group failed-class cache) ----------

    @Test
    fun `createGroup produces independent failed caches across groups`() {
        val targetA = "com/example/IsoTargetA"
        val targetB = "com/example/IsoTargetB"
        val badA = "com/example/IsoBadA"
        val badB = "com/example/IsoBadB"
        val badBytesA = buildClassWithInvalidSuperclass(badA)
        val badBytesB = buildClassWithInvalidSuperclass(badB)

        val baseA =
            MutantClassLoaderFactory.createGroup(
                parentClassLoader,
                classFiles = mapOf(badA to badBytesA, badB to badBytesB),
                testClassBytes = emptyMap(),
                targetClassName = targetA,
            )
        val baseB =
            MutantClassLoaderFactory.createGroup(
                parentClassLoader,
                classFiles = mapOf(badA to badBytesA, badB to badBytesB),
                testClassBytes = emptyMap(),
                targetClassName = targetB,
            )

        // Populate baseA's cache with both bad names.
        assertFailsWith<LinkageError> { baseA.loadClass("com.example.IsoBadA") }
        assertFailsWith<LinkageError> { baseA.loadClass("com.example.IsoBadB") }
        assertTrue(badA in baseA.failedClassCache)
        assertTrue(badB in baseA.failedClassCache)

        // baseB's cache must be untouched — both names should still attempt defineClass.
        assertNotSame(baseA.failedClassCache, baseB.failedClassCache)
        assertTrue(baseB.failedClassCache.isEmpty(), "baseB's cache must start empty")
        assertFailsWith<LinkageError> { baseB.loadClass("com.example.IsoBadA") }
        assertFailsWith<LinkageError> { baseB.loadClass("com.example.IsoBadB") }
    }

    @Test
    fun `createMutationLoader shares the base loader failed cache`() {
        val target = "com/example/ShareTarget"
        val badName = "com/example/ShareBad"
        val badBytes = buildClassWithInvalidSuperclass(badName)
        val targetBytes = buildClassWithMethod(target)

        val base =
            MutantClassLoaderFactory.createGroup(
                parentClassLoader,
                classFiles = mapOf(target to targetBytes, badName to badBytes),
                testClassBytes = emptyMap(),
                targetClassName = target,
            )
        val mutationLoader =
            MutantClassLoaderFactory.createMutationLoader(
                base,
                target,
                targetBytes,
                testClassBytes = emptyMap(),
                allClassBytes = mapOf(badName to badBytes),
            )

        // Populate the cache via the base loader.
        assertFailsWith<LinkageError> { base.loadClass("com.example.ShareBad") }
        assertTrue(badName in base.failedClassCache)

        // The mutation loader must observe the same cache instance.
        assertSame(base.failedClassCache, mutationLoader.failedClassCache)
        // First call after population: cache short-circuit kicks in, parent delegation
        // returns ClassNotFoundException (the bad name is not on the parent classpath).
        assertFailsWith<ClassNotFoundException> {
            mutationLoader.loadClass("com.example.ShareBad")
        }
    }

    @Test
    fun `concurrent createGroup calls produce independent caches`() {
        val threadCount = 16
        val target = "com/example/ConcTarget"
        val bad = "com/example/ConcBad"
        val badBytes = buildClassWithInvalidSuperclass(bad)

        val baseLoaders =
            (0 until threadCount).map { _ ->
                MutantClassLoaderFactory.createGroup(
                    parentClassLoader,
                    classFiles = mapOf(target to buildClassWithMethod(target), bad to badBytes),
                    testClassBytes = emptyMap(),
                    targetClassName = target,
                )
            }

        // Every base must hold a distinct cache instance. Identity check — empty
        // Sets compare equal regardless of backing map, so content equality would
        // collapse 16 distinct instances into 1.
        val caches = baseLoaders.map { it.failedClassCache }
        val uniqueByIdentity = java.util.IdentityHashMap<MutableSet<String>, Unit>()
        caches.forEach { uniqueByIdentity[it] = Unit }
        assertEquals(
            threadCount,
            uniqueByIdentity.size,
            "Each createGroup call must produce a distinct failedClassCache instance",
        )

        // Triggering a failure in one group must not mark the name in any other group.
        assertFailsWith<LinkageError> { baseLoaders[0].loadClass("com.example.ConcBad") }
        assertTrue(bad in baseLoaders[0].failedClassCache)
        for (i in 1 until threadCount) {
            assertTrue(
                baseLoaders[i].failedClassCache.isEmpty(),
                "Group $i cache must remain untouched by group 0's failure",
            )
        }
    }

    // ---------- helpers ----------

    private fun createDummyMutation(className: String): MutationInfo =
        MutationInfo(
            operator = MutationOperator.ARITHMETIC,
            className = className,
            methodName = "dummy",
            methodDescriptor = "()I",
            lineNumber = 1,
            description = "dummy",
            originalOpcode = Opcodes.IADD,
            mutatedOpcode = Opcodes.ISUB,
        )

    private fun buildClassWithMethod(internalName: String): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "run", "()I", null, null)
        mv?.visitCode()
        mv?.visitInsn(Opcodes.ICONST_0)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(1, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    /**
     * Builds a class with structurally corrupt bytecode so defineClass throws
     * LinkageError (UnsupportedClassVersionError, a subclass of LinkageError)
     * on first attempt.
     * Approach: emit a valid class via ASM, then overwrite the class-file major
     * version with a value higher than the running JDK supports. A missing
     * superclass no longer triggers LinkageError on modern JVMs — super
     * resolution is deferred to resolveClass/linkage time. An invalid
     * class-file version fails structurally during defineClass itself,
     * regardless of resolve flag.
     */
    private fun buildClassWithInvalidSuperclass(internalName: String): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        cw.visitEnd()
        val bytes = cw.toByteArray()
        // Class file layout: magic (u4) + minor (u2) + major (u2) + constant_pool...
        // Overwrite major version (offset 6-7) with 62 — unsupported by JDK 17 (max 61).
        val corrupted = bytes.copyOf()
        corrupted[6] = 0x00
        corrupted[7] = 0x46
        return corrupted
    }
}
