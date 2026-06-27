package com.github.rodrigotimoteo.mutation.runner

import com.github.rodrigotimoteo.mutation.DEFAULT_TIMEOUT_MS
import com.github.rodrigotimoteo.mutation.engine.MutationEngine
import com.github.rodrigotimoteo.mutation.model.MutationReport
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files

/**
 * Test runner that executes mutation testing using JUnit Platform.
 * Can be used standalone or via Gradle plugin.
 */
class MutationTestRunner(
    private val engine: MutationEngine,
) {
    private val logger = LoggerFactory.getLogger(MutationTestRunner::class.java)

    /**
     * Default test-class name patterns. Matches the common JUnit 5/4
     * conventions plus JUnit Platform suite containers, which would
     * otherwise be invisible to the engine — a `*Suite` class is the
     * typical JUnit 5 entry point for grouped test execution and
     * contains no `@Test` methods of its own, so missing them from
     * discovery also means missing the tests they include.
     */
    private val defaultTestClassNamePatterns: Set<String> =
        setOf("*Test", "*Tests", "Test*", "*Spec", "*Suite")

    /**
     * Runs mutation testing on a project.
     *
     * @param testClassNamePatterns Glob patterns matched against the
     *   simple class name of each candidate `.class` file. Default
     *   covers JUnit 5/4 conventions plus `*Suite` containers. Override
     *   to support a non-default naming scheme (e.g. a project that
     *   uses `*IT` for integration tests).
     */
    fun run(
        classesDir: File,
        testClassesDir: File,
        classpath: List<File>,
        coverageExecFile: File? = null,
        testClassNamePatterns: Set<String> = defaultTestClassNamePatterns,
    ): MutationReport {
        logger.info("Loading classes from: $classesDir")
        logger.info("Loading test classes from: $testClassesDir")

        // Load all class files
        val classFiles = loadClassFiles(classesDir)
        logger.info("Loaded ${classFiles.size} class files")

        // Load test class files
        val testClassBytes = loadClassFiles(testClassesDir)
        logger.info("Loaded ${testClassBytes.size} test class files")

        // Find test classes
        val testClassNames = findTestClasses(testClassesDir, testClassNamePatterns)
        logger.info("Found ${testClassNames.size} test classes: $testClassNames")

        // Create classloader from classpath so engine can resolve all dependencies.
        // Parent is the platform classloader to prevent the test classloader from
        // inheriting conflicting library versions (e.g. coroutines, dagger) from the
        // engine's classloader, which would cause LinkageError when the user's
        // bytecode (compiled against a different version) is linked.
        //
        // **Classpath requirements** (enforced by the caller — see plugin README):
        //   1. The `classpath` argument MUST include the full test runtime
        //      classpath (production + test sources + all transitive
        //      dependencies) so that inline-mock agents and instrumented
        //      classes (MockK inline, Mockito inline, ByteBuddy) can be
        //      resolved by the test classloader. A truncated classpath
        //      causes `ClassNotFoundException` at first use of a mocked
        //      class and reports the mutation as `NO_COVERAGE`.
        //   2. Any `-javaagent` JVM args that the test JVM needs (MockK
        //      inline, Mockito inline, Jacoco) MUST already be set on the
        //      calling process; MutKt does not append agents itself.
        //      The Gradle plugin preserves the existing test JVM args
        //      when wiring the mutation test task.
        val urls = classpath.map { it.toURI().toURL() }.toTypedArray()
        val testClassLoader =
            java.net.URLClassLoader(
                urls,
                ClassLoader.getPlatformClassLoader(),
            )

        return try {
            // Run mutation testing with the test classloader
            engine.runMutationTesting(classFiles, testClassNames, testClassBytes, coverageExecFile, testClassLoader)
        } finally {
            testClassLoader.close()
        }
    }

    private fun loadClassFiles(dir: File): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        if (!dir.exists() || !dir.isDirectory) return result

        Files.walk(dir.toPath()).use { stream ->
            stream.filter { it.toString().endsWith(".class") }
                .forEach { path ->
                    val relativePath = dir.toPath().relativize(path)
                    val className =
                        relativePath.toString()
                            .replace(".class", "")
                            .replace("/", ".")
                            .replace("\\", ".")
                    val bytes = Files.readAllBytes(path)
                    result[className.replace('.', '/')] = bytes
                }
        }
        return result
    }

    @JvmOverloads
    private fun findTestClasses(
        dir: File,
        testClassNamePatterns: Set<String> = defaultTestClassNamePatterns,
    ): List<String> {
        val result = mutableListOf<String>()
        if (!dir.exists() || !dir.isDirectory) return result

        Files.walk(dir.toPath()).use { stream ->
            stream.filter { it.toString().endsWith(".class") }
                .forEach { path ->
                    val relativePath = dir.toPath().relativize(path)
                    val className =
                        relativePath.toString()
                            .replace(".class", "")
                            .replace("/", ".")
                            .replace("\\", ".")
                    val simpleName = className.substringAfterLast('.')
                    // Allow static nested test classes (contain $) — JUnit 4 and Kotlin
                    // `companion object` tests are compiled as `Outer$Inner` and need
                    // to be discoverable here. Only exclude anonymous classes, which
                    // have a numeric suffix (e.g. `Foo$1`, `Foo$2`).
                    if (className.contains("$")) {
                        val innerSegment = className.substringAfterLast('$')
                        if (innerSegment.toIntOrNull() != null) return@forEach
                    }
                    if (matchesTestPattern(simpleName, testClassNamePatterns)) {
                        result.add(className)
                    }
                }
        }
        return result
    }

    /**
     * Match a simple class name against a set of glob-style patterns.
     * Supported wildcards: `*` matches zero-or-more characters anywhere
     * in the pattern. A pattern with no `*` requires an exact match.
     * Patterns are anchored to the full simple name (no implicit
     * suffix/prefix), so `Test*` and `*Test` are distinct.
     */
    private fun matchesTestPattern(
        simpleName: String,
        patterns: Set<String>,
    ): Boolean {
        if (patterns.isEmpty()) return false
        for (pattern in patterns) {
            if (pattern == "*") return true
            val regex =
                pattern
                    .split("*")
                    .joinToString(separator = ".*") { java.util.regex.Pattern.quote(it) }
                    .let { "^$it$" }
                    .toRegex()
            if (regex.matches(simpleName)) return true
        }
        return false
    }
}

/**
 * Factory for creating MutationTestRunner with default configuration.
 */
object MutationTestRunnerFactory {
    fun create(
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        maxParallelMutants: Int = 4,
        enabledOperators: Set<MutationOperator> = MutationOperator.MVP_OPERATORS,
        enableInlinedFinally: Boolean = true,
        enableTestOrdering: Boolean = true,
        changedClasses: Set<String> = emptySet(),
        includePatterns: List<String> = emptyList(),
        excludePatterns: List<String> = emptyList(),
        enableCache: Boolean = false,
        enableSubsumption: Boolean = false,
        enableWeakMutation: Boolean = false,
        projectDir: java.io.File? = null,
        excludedMethods: Set<String> = emptySet(),
        maxMutationsPerClass: Int = 0,
        targetTestPatterns: List<String> = emptyList(),
        excludeTestPatterns: List<String> = emptyList(),
        includeTags: Set<String> = emptySet(),
        excludeTags: Set<String> = emptySet(),
        engineIds: List<String> =
            listOf(
                "junit-jupiter",
                "junit-vintage",
                "junit-platform-suite-engine",
            ),
    ): MutationTestRunner {
        val engine =
            MutationEngine(
                enabledOperators = enabledOperators,
                timeoutMs = timeoutMs,
                maxParallelMutants = maxParallelMutants,
                enableInlinedFinally = enableInlinedFinally,
                enableTestOrdering = enableTestOrdering,
                changedClasses = changedClasses,
                includePatterns = includePatterns,
                excludePatterns = excludePatterns,
                enableCache = enableCache,
                enableSubsumption = enableSubsumption,
                enableWeakMutation = enableWeakMutation,
                projectDir = projectDir,
                excludedMethods = excludedMethods,
                maxMutationsPerClass = maxMutationsPerClass,
                targetTestPatterns = targetTestPatterns,
                excludeTestPatterns = excludeTestPatterns,
                includeTags = includeTags,
                excludeTags = excludeTags,
                engineIds = engineIds,
            )
        return MutationTestRunner(engine)
    }
}
