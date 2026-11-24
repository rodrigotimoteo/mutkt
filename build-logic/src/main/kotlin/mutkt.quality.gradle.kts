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
