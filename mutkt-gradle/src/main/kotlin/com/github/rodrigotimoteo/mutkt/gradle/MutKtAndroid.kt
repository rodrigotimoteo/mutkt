package com.github.rodrigotimoteo.mutkt.gradle

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasUnitTest
import com.android.build.api.variant.Variant
import org.gradle.api.GradleException
import org.gradle.api.Project

/** Fails the build, once AGP created its variants, unless the `variant` DSL names one with unit tests. */
internal fun Project.checkAndroidVariant(extension: MutKtExtension) {
    val components = extensions.getByType(AndroidComponentsExtension::class.java)
    // Variant name to whether it has a unit-test component.
    val variants = mutableMapOf<String, Boolean>()
    components.onVariants(components.selector().all()) { variant: Variant ->
        variants[variant.name] = (variant as? HasUnitTest)?.unitTest != null
    }
    // Registered after AGP's own afterEvaluate, which runs the onVariants callbacks.
    afterEvaluate {
        val wanted = extension.variant.get()
        val problem =
            when (variants[wanted]) {
                null -> "MutKt cannot find variant '$wanted'"
                false -> "MutKt variant '$wanted' has no unit tests"
                true -> return@afterEvaluate
            }
        val choices = variants.filterValues { hasUnitTest -> hasUnitTest }.keys.sorted()
        throw GradleException("$problem; variants with unit tests: ${choices.joinToString().ifEmpty { "none" }}")
    }
}
