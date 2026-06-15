package com.github.rodrigotimoteo.mutation.gradle

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class MutationPluginExtensionTest {
    private val project = ProjectBuilder.builder().build()
    private val extension = MutationPluginExtension(project)

    @Test
    fun `default operators are MVP_OPERATORS with 6 operators`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(MutationPlugin::class.java)
        val ext = project.extensions.getByType(MutationPluginExtension::class.java)
        assertThat(ext.enabledOperators.get()).hasSize(6)
    }

    @Test
    fun `maxParallelMutants defaults to 4 for build-cache determinism`() {
        assertThat(extension.maxParallelMutants.get())
            .isEqualTo(4)
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
    fun `failOnMutationScoreThreshold defaults to 0`() {
        assertThat(extension.failOnMutationScoreThreshold.get()).isEqualTo(0)
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
        assertThat(extension.outputDir.get().asFile.path)
            .endsWith("build" + java.io.File.separator + "reports" + java.io.File.separator + "mutation")
    }

    @Test
    fun `reportFormats defaults to html only`() {
        val formats = extension.reportFormats.get()
        assertThat(formats).containsExactly("html")
    }

    @Test
    fun `custom operators can be set`() {
        extension.enabledOperators.set(setOf("ARITHMETIC", "RETURN_VALS"))
        assertThat(extension.enabledOperators.get()).contains("ARITHMETIC", "RETURN_VALS")
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

    // === Android Support ===

    @Test
    fun `isAndroid defaults to false`() {
        assertThat(extension.isAndroid.get()).isFalse()
    }

    @Test
    fun `androidPluginType defaults to empty string`() {
        assertThat(extension.androidPluginType.get()).isEmpty()
    }

    @Test
    fun `androidVariant defaults to debug`() {
        assertThat(extension.androidVariant.get()).isEqualTo("debug")
    }

    @Test
    fun `excludeGeneratedClasses defaults to expected Android and Kotlin generated patterns`() {
        val excluded = extension.excludeGeneratedClasses.get()
        assertThat(excluded).contains(
            "**/R",
            "**/R\$*",
            "**/BuildConfig",
            "**/ComposableSingletons\$*",
            "**/databinding/**",
            "**/BR",
            "**/*_Factory",
            "**/*_MembersInjector",
            "**/*_GeneratedInjector",
            "**/*\$Lambda\$*",
            "**/*\$inlined\$*",
            "**/META-INF/**",
        )
    }

    @Test
    fun `isAndroid can be set to true to simulate Android plugin applied`() {
        extension.isAndroid.set(true)
        assertThat(extension.isAndroid.get()).isTrue()
    }

    @Test
    fun `androidPluginType can be set to application`() {
        extension.androidPluginType.set("application")
        assertThat(extension.androidPluginType.get()).isEqualTo("application")
    }

    @Test
    fun `androidPluginType can be set to library`() {
        extension.androidPluginType.set("library")
        assertThat(extension.androidPluginType.get()).isEqualTo("library")
    }

    @Test
    fun `androidVariant can be set to release`() {
        extension.androidVariant.set("release")
        assertThat(extension.androidVariant.get()).isEqualTo("release")
    }

    @Test
    fun `excludeGeneratedClasses can be customized`() {
        extension.excludeGeneratedClasses.set(setOf("**/MyGen*"))
        assertThat(extension.excludeGeneratedClasses.get()).contains("**/MyGen*")
    }
}
