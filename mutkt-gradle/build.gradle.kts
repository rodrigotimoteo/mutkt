import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("mutkt.published-library")
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        create("mutkt") {
            id = "io.github.rodrigotimoteo.mutkt"
            implementationClass = "com.github.rodrigotimoteo.mutkt.gradle.MutKtPlugin"
        }
    }
}

// Gradle 8.10 Kotlin DSL embeds Kotlin 1.8 and cannot read newer API metadata of this plugin's classes.
kotlin {
    compilerOptions {
        languageVersion = KotlinVersion.KOTLIN_1_8
        apiVersion = KotlinVersion.KOTLIN_1_8
    }
}

tasks.withType<Test>().configureEach {
    val dslDoc = rootProject.layout.projectDirectory.file("docs/dsl.md")
    systemProperty("mutkt.dslDoc", dslDoc.asFile.absolutePath)
    // The end-to-end golden file is rewritten with -Dmutkt.updateGolden=true.
    val goldenDir = layout.projectDirectory.dir("src/test/resources/e2e")
    systemProperty("mutkt.goldenDir", goldenDir.asFile.absolutePath)
    providers.systemProperty("mutkt.updateGolden").orNull?.let { systemProperty("mutkt.updateGolden", it) }
}

tasks.test {
    useJUnitPlatform { excludeTags("e2e") }
}

// Own JVM: the end-to-end timeout mutant leaves a spinning thread that must not outlive its test JVM's other tests.
val e2eTest by tasks.registering(Test::class) {
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    useJUnitPlatform { includeTags("e2e") }
}

tasks.check {
    dependsOn(e2eTest)
}

dependencies {
    implementation(project(":mutkt-core"))
    implementation(project(":mutkt-annotations"))
    implementation(project(":mutkt-asm"))
    implementation(project(":mutkt-junit"))
    implementation(project(":mutkt-report"))

    testImplementation(project(":mutkt-testing"))
    testImplementation(gradleTestKit())
}
