import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask

plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}

ktlint {
    version = libs.versions.ktlint.get()
}

// kotlin-dsl adds generated sources under build/ to the main source set; lint only what is written by hand.
tasks.withType<KtLintCheckTask>().matching { "SourceSet" in it.name }.configureEach {
    setSource(fileTree("src").matching { include("**/*.kt") })
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.kover.gradle.plugin)
}
