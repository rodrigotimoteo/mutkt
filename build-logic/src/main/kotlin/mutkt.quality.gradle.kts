import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

val libs = the<VersionCatalogsExtension>().named("libs")

ktlint {
    version = libs.findVersion("ktlint").get().requiredVersion
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

// ForbiddenMethodCall needs type resolution, which only the per-source-set tasks have.
pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    tasks.named("detekt") { dependsOn("detektMain") }
}

// detekt 1.23 resolves types with Kotlin 2.0, which cannot read a newer stdlib's metadata: calls then look unresolved
// (UnreachableCode false positives). The stdlib of detekt's own Kotlin stands in on its type-resolution classpath.
val detektStdlib =
    configurations.create("detektStdlib") {
        isCanBeConsumed = false
    }

dependencies {
    detektStdlib(libs.findLibrary("kotlin-stdlib-detekt").get())
}

// The plain detekt task runs without type resolution; an added stdlib would turn it on with an incomplete classpath.
tasks.withType<Detekt>().matching { it.name != "detekt" }.configureEach {
    val compiled = files(classpath.from.toList()).filter { !it.name.startsWith("kotlin-stdlib") }
    classpath.setFrom(compiled, detektStdlib)
}
