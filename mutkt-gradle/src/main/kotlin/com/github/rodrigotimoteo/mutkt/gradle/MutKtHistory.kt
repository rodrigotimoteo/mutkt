package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.application.AnalysisConfig
import com.github.rodrigotimoteo.mutkt.application.HistoryReuse
import com.github.rodrigotimoteo.mutkt.application.RunFingerprint
import com.github.rodrigotimoteo.mutkt.application.RunMutationAnalysis
import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.ClassSource
import com.github.rodrigotimoteo.mutkt.asm.AsmMutantGenerator
import com.github.rodrigotimoteo.mutkt.junit.ForkedTestExecutor
import com.github.rodrigotimoteo.mutkt.report.ConsoleReportWriter
import java.nio.file.Path

/**
 * The result history of this run (ADR 0011): the file, the fingerprint of everything outside the main and test classes
 * that can change a result, and the staged [tests]. [production] and [projectClasses] are the staged main classes
 * alone and with the support classes; [dependencies] are the test runtime entries that are not class directories.
 */
internal fun MutKtTask.historyReuse(
    production: Staged,
    projectClasses: Staged,
    tests: Staged,
    dependencies: List<Path>,
    config: AnalysisConfig,
): HistoryReuse {
    val store = JsonFileHistory(historyFile.get().asFile)
    val digests = store.digests
    val testClasses = DirectoryClassSource(tests.dir).classes()
    val shapes = testClassShapes(testClasses)
    // Support classes (test fixtures) load next to the main classes, but no key names them.
    val support =
        if (projectClasses == production) {
            emptyList()
        } else {
            DirectoryClassSource(projectClasses.dir).classes().filter { it.name !in production.origins }
        }
    val fingerprint =
        RunFingerprint(
            pluginVersion = pluginCode(digests),
            operators = operators.get(),
            engines = engines.get(),
            includeTags = includeTags.get(),
            excludeTags = excludeTags.get(),
            timeout = config.timeout,
            threads = config.parallelism,
            jvmArgs = testJvmArgs.get(),
            java = testJavaRuntime.get(),
            workingDir = testWorkingDir.get(),
            classpath = dependencies.associateTo(LinkedHashMap()) { "$it" to contentDigest(it, digests::digest) },
            helperTestClasses =
                (testClasses.filter { it.name in shapes.helpers } + support).associate { it.name to sha256(it.bytes) },
        )
    val source =
        object : ClassSource {
            override fun classes(): List<ClassFile> = testClasses
        }
    return HistoryReuse(store, fingerprint, source, shapes.supertypes)
}

// MutKt's own jars by content rather than its version, so a snapshot build that changes an operator drops the history.
private fun pluginCode(digests: FileDigests): String =
    listOf(
        MutKtPlugin::class.java,
        RunMutationAnalysis::class.java,
        AsmMutantGenerator::class.java,
        ForkedTestExecutor::class.java,
        ConsoleReportWriter::class.java,
    ).map { Path.of(checkNotNull(it.protectionDomain.codeSource) { "No code source for $it" }.location.toURI()) }
        .distinct()
        .joinToString(" ") { contentDigest(it, digests::digest) }
