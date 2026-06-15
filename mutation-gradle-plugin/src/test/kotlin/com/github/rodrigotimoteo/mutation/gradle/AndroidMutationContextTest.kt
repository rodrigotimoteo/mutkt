package com.github.rodrigotimoteo.mutation.gradle

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import java.io.File

class AndroidMutationContextTest {
    private val objects = ProjectBuilder.builder().build().objects

    @Test
    fun `context exposes all configured fields via getters`() {
        val runtimeCp = objects.fileCollection().from(File("runtime.jar"))
        val classes = objects.fileCollection().from(File("classes"))
        val testClasses = objects.fileCollection().from(File("test-classes"))
        val androidJar = File("android.jar")
        val mainDir = File("build/tmp/kotlin-classes/debug")
        val testDir = File("build/tmp/kotlin-classes/debugUnitTest")
        val ctx =
            AndroidMutationContext(
                variantName = "debug",
                runtimeClasspath = runtimeCp,
                classesDirs = classes,
                testClassesDirs = testClasses,
                mainClassesDir = mainDir,
                testClassesDir = testDir,
                androidJar = androidJar,
                compileTask = "compileDebugKotlin",
                testCompileTask = "compileDebugUnitTestKotlin",
            )
        assertThat(ctx.variantName).isEqualTo("debug")
        assertThat(ctx.runtimeClasspath).isSameAs(runtimeCp)
        assertThat(ctx.classesDirs).isSameAs(classes)
        assertThat(ctx.testClassesDirs).isSameAs(testClasses)
        assertThat(ctx.mainClassesDir).isEqualTo(mainDir)
        assertThat(ctx.testClassesDir).isEqualTo(testDir)
        assertThat(ctx.androidJar).isEqualTo(androidJar)
        assertThat(ctx.compileTask).isEqualTo("compileDebugKotlin")
        assertThat(ctx.testCompileTask).isEqualTo("compileDebugUnitTestKotlin")
    }

    @Test
    fun `data class copy returns a context with the same string fields`() {
        val a = makeContext("debug", "compileDebugKotlin", "compileDebugUnitTestKotlin")
        val b = a.copy()
        assertThat(b.variantName).isEqualTo(a.variantName)
        assertThat(b.compileTask).isEqualTo(a.compileTask)
        assertThat(b.testCompileTask).isEqualTo(a.testCompileTask)
        assertThat(b.androidJar).isEqualTo(a.androidJar)
    }

    @Test
    fun `data class toString includes the variant name`() {
        val ctx = makeContext("debug", "compileDebugKotlin", "compileDebugUnitTestKotlin")
        assertThat(ctx.toString()).contains("debug")
    }

    @Test
    fun `copy preserves unmodified fields`() {
        val a = makeContext("debug", "compileDebugKotlin", "compileDebugUnitTestKotlin")
        val b = a.copy(compileTask = "compileReleaseKotlin")
        assertThat(b.variantName).isEqualTo("debug")
        assertThat(b.compileTask).isEqualTo("compileReleaseKotlin")
        assertThat(b.testCompileTask).isEqualTo(a.testCompileTask)
    }

    @Test
    fun `variant name is preserved verbatim including case`() {
        val ctx = makeContext("Release", "compileReleaseKotlin", "compileReleaseUnitTestKotlin")
        assertThat(ctx.variantName).isEqualTo("Release")
    }

    private fun makeContext(
        variant: String,
        compileTask: String,
        testCompileTask: String,
    ): AndroidMutationContext {
        val androidJar = File("android.jar")
        return AndroidMutationContext(
            variantName = variant,
            runtimeClasspath = objects.fileCollection(),
            classesDirs = objects.fileCollection(),
            testClassesDirs = objects.fileCollection(),
            mainClassesDir = null,
            testClassesDir = null,
            androidJar = androidJar,
            compileTask = compileTask,
            testCompileTask = testCompileTask,
        )
    }
}
