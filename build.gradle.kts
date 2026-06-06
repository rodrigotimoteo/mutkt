plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.dokka)
    alias(libs.plugins.owasp.dependencycheck)
    id("com.vanniktech.maven.publish") version "0.30.0"
}

allprojects {
    group = "io.github.rodrigotimoteo"
    version = "0.1.1"

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

// Dokka V2 multi-module aggregation
dependencies {
    dokka(project(":mutation-core"))
    dokka(project(":mutation-test-runner"))
    dokka(project(":mutation-gradle-plugin"))
    dokka(project(":mutation-sample"))
}

subprojects {
    if (name == "mutation-sample") return@subprojects

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")
    apply(plugin = "org.jetbrains.kotlinx.kover")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "com.vanniktech.maven.publish")

    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
    }

    extensions.configure<PublishingExtension> {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/rodrigotimoteo/mutkt")
                credentials {
                    username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String? ?: "rodrigotimoteo"
                    password =
                        System.getenv("GH_PACKAGES_TOKEN")
                            ?: System.getenv("GITHUB_TOKEN")
                            ?: project.findProperty("gpr.key") as String?
                            ?: ""
                }
            }
        }
    }

    mavenPublishing {
        publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
        signAllPublications()
        pom {
            name.set("mutkt")
            description.set("Kotlin Mutation Testing - PITest-style for Kotlin/JVM")
            url.set("https://github.com/rodrigotimoteo/mutkt")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("rodrigotimoteo")
                    name.set("Rodrigo Timoteo")
                    email.set("rodrigo.timoteo2603@gmail.com")
                }
            }
            scm {
                connection.set("scm:git:git://github.com/rodrigotimoteo/mutkt.git")
                developerConnection.set("scm:git:ssh://github.com/rodrigotimoteo/mutkt.git")
                url.set("https://github.com/rodrigotimoteo/mutkt")
            }
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = libs.versions.jvmTarget.get()
            freeCompilerArgs +=
                listOf(
                    "-opt-in=kotlin.RequiresOptIn",
                    "-Xjsr305=strict",
                )
        }
    }
}

extensions.configure<org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension> {
    failOnError.set(false)
    autoUpdate.set(false)
    nvd {
        apiKey = System.getenv("NVD_API_KEY") ?: (project.findProperty("nvd.api.key") as String? ?: "")
        validForHours.set(24)
        datafeedUrl.set("https://nvd.nist.gov/static/feeds/json/cve/1.1/nvdcve-1.1-{year}.json.gz")
        datafeedStartYear.set(2002)
    }
}
