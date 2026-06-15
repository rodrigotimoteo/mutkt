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
     * Runs mutation testing on a project.
     */
    fun run(
        classesDir: File,
        testClassesDir: File,
        classpath: List<File>,
        coverageExecFile: File? = null,
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
        val testClassNames = findTestClasses(testClassesDir)
        logger.info("Found ${testClassNames.size} test classes: $testClassNames")

        // Create classloader from classpath so engine can resolve all dependencies.
        // Parent is the platform classloader to prevent the test classloader from
        // inheriting conflicting library versions (e.g. coroutines, dagger) from the
        // engine's classloader, which would cause LinkageError when the user's
        // bytecode (compiled against a different version) is linked.
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

    private fun findTestClasses(dir: File): List<String> {
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
                    // Exclude inner/nested classes (contain $) — ReflectionTestRunner discovers them via @Nested
                    if (className.contains("$")) return@forEach
                    // Match classes named *Test, *Tests, Test*, or *Spec
                    if (simpleName.endsWith("Test") ||
                        simpleName.endsWith("Tests") ||
                        simpleName.startsWith("Test") ||
                        simpleName.endsWith("Spec")
                    ) {
                        result.add(className)
                    }
                }
        }
        return result
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
            )
        return MutationTestRunner(engine)
    }
}
