plugins {
    id("mutkt.kotlin-library") apply false
    id("io.github.rodrigotimoteo.mutkt") apply false
}

// Each project compiles the sources and runs the tests of one repository module, so the mutated classes are the
// module's own and the plugin never has to be applied to the build that produces it.
subprojects {
    val module = rootDir.resolve("../../mutkt-$name")
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        extensions.configure<SourceSetContainer> {
            named("main") { resources.setSrcDirs(listOf(module.resolve("src/main/resources"))) }
            named("test") { resources.setSrcDirs(listOf(module.resolve("src/test/resources"))) }
        }
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            sourceSets.named("main") { kotlin.setSrcDirs(listOf(module.resolve("src/main/kotlin"))) }
            sourceSets.named("test") {
                kotlin.setSrcDirs(listOf(module.resolve("src/test/kotlin")))
                // These read the repository root and its docs, not the code under mutation.
                kotlin.exclude("**/ArchitectureTest.kt", "**/ParityDocTest.kt", "**/OperatorDocTest.kt")
            }
        }
    }
}
