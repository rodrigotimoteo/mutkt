package com.github.rodrigotimoteo.mutation.gradle

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class MutationPluginExtensionTest {
    private val project = ProjectBuilder.builder().build()
    private val extension = MutationPluginExtension(project)

    @Test
    fun `default operators are MVP_OPERATORS with 7 operators`() {
        val operators = extension.enabledOperators.get()
        assertThat(operators).hasSize(7)
        assertThat(operators).contains(
            "CONDITIONALS_BOUNDARY",
            "NEGATE_CONDITIONALS",
            "ARITHMETIC",
            "RETURN_VALS",
            "NULL_RETURNS",
            "EMPTY_RETURNS",
            "INVERT_NEGS",
        )
    }

    @Test
    fun `maxParallelMutants defaults to availableProcessors`() {
        assertThat(extension.maxParallelMutants.get())
            .isEqualTo(Runtime.getRuntime().availableProcessors())
    }

    @Test
    fun `excludedClasses defaults to 15 patterns`() {
        val excluded = extension.excludedClasses.get()
        assertThat(excluded).hasSize(15)
        assertThat(excluded).contains("**/*Test", "**/*Tests", "**/BuildConfig")
    }

    @Test
    fun `excludedMethods defaults to 6 methods`() {
        val methods = extension.excludedMethods.get()
        assertThat(methods).hasSize(6)
        assertThat(methods).contains("main", "toString", "hashCode", "equals", "clone", "finalize")
    }

    @Test
    fun `targetClassPatterns defaults to emptySet`() {
        assertThat(extension.targetClassPatterns.get()).isEmpty()
    }

    @Test
    fun `targetTestPatterns defaults to emptySet`() {
        assertThat(extension.targetTestPatterns.get()).isEmpty()
    }

    @Test
    fun `excludeClassPatterns defaults to emptySet`() {
        assertThat(extension.excludeClassPatterns.get()).isEmpty()
    }

    @Test
    fun `excludeTestPatterns defaults to emptySet`() {
        assertThat(extension.excludeTestPatterns.get()).isEmpty()
    }

    @Test
    fun `enableSubsumption defaults to true`() {
        assertThat(extension.enableSubsumption.get()).isTrue()
    }

    @Test
    fun `enableWeakMutation defaults to true`() {
        assertThat(extension.enableWeakMutation.get()).isTrue()
    }

    @Test
    fun `enableInlinedFinally defaults to true`() {
        assertThat(extension.enableInlinedFinally.get()).isTrue()
    }

    @Test
    fun `enableTestOrdering defaults to true`() {
        assertThat(extension.enableTestOrdering.get()).isTrue()
    }

    @Test
    fun `enableIncrementalAnalysis defaults to true`() {
        assertThat(extension.enableIncrementalAnalysis.get()).isTrue()
    }

    @Test
    fun `showClassScores defaults to true`() {
        assertThat(extension.showClassScores.get()).isTrue()
    }

    @Test
    fun `generateGraph defaults to false`() {
        assertThat(extension.generateGraph.get()).isFalse()
    }

    @Test
    fun `timeoutMs defaults to 30000`() {
        assertThat(extension.timeoutMs.get()).isEqualTo(30000L)
    }

    @Test
    fun `failOnSurvived defaults to false`() {
        assertThat(extension.failOnSurvived.get()).isFalse()
    }

    @Test
    fun `failOnScoreThreshold defaults to 0`() {
        assertThat(extension.failOnScoreThreshold.get()).isEqualTo(0)
    }

    @Test
    fun `failOnCoverageThreshold defaults to 0`() {
        assertThat(extension.failOnCoverageThreshold.get()).isEqualTo(0)
    }

    @Test
    fun `mutantTimeoutMs defaults to 0`() {
        assertThat(extension.mutantTimeoutMs.get()).isEqualTo(0L)
    }

    @Test
    fun `maxMutationsPerClass defaults to 0`() {
        assertThat(extension.maxMutationsPerClass.get()).isEqualTo(0)
    }

    @Test
    fun `autoRunJaCoCo defaults to true`() {
        assertThat(extension.autoRunJaCoCo.get()).isTrue()
    }

    @Test
    fun `outputDir defaults to build reports mutation`() {
        assertThat(extension.outputDir.get()).isEqualTo("build/reports/mutation")
    }

    @Test
    fun `reportFormats defaults to html only`() {
        val formats = extension.reportFormats.get()
        assertThat(formats).containsExactly("html")
    }

    @Test
    fun `custom operators can be set`() {
        extension.enabledOperators.set(setOf("ARITHMETIC", "INVERT_NEGS"))
        assertThat(extension.enabledOperators.get()).contains("ARITHMETIC", "INVERT_NEGS")
    }

    @Test
    fun `timeoutMs can be set`() {
        extension.timeoutMs.set(60000L)
        assertThat(extension.timeoutMs.get()).isEqualTo(60000L)
    }

    @Test
    fun `maxParallelMutants can be set`() {
        extension.maxParallelMutants.set(8)
        assertThat(extension.maxParallelMutants.get()).isEqualTo(8)
    }

    @Test
    fun `failOnScoreThreshold can be set`() {
        extension.failOnScoreThreshold.set(80)
        assertThat(extension.failOnScoreThreshold.get()).isEqualTo(80)
    }

    @Test
    fun `generateGraph can be set`() {
        extension.generateGraph.set(true)
        assertThat(extension.generateGraph.get()).isTrue()
    }

    @Test
    fun `enableIncrementalAnalysis can be set to false`() {
        extension.enableIncrementalAnalysis.set(false)
        assertThat(extension.enableIncrementalAnalysis.get()).isFalse()
    }

    @Test
    fun `targetClassPatterns can be set`() {
        extension.targetClassPatterns.set(setOf("com\\.example\\..*"))
        assertThat(extension.targetClassPatterns.get()).contains("com\\.example\\..*")
    }

    @Test
    fun `excludedClasses can be customized`() {
        extension.excludedClasses.set(setOf("**/generated/**"))
        assertThat(extension.excludedClasses.get()).contains("**/generated/**")
    }
}
