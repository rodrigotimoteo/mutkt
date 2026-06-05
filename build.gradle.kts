plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.dokka)
}

allprojects {
    repositories {
        mavenCentral()
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

allprojects {
    group = "io.github.rodrigotimoteo"
    version = "0.1.0"

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
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

    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
        withJavadocJar()
    }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])

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
        }

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
            maven {
                name = "MavenCentral"
                url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
                credentials {
                    username = System.getenv("SONATYPE_USERNAME") ?: project.findProperty("ossrh.username") as String? ?: ""
                    password = System.getenv("SONATYPE_PASSWORD") ?: project.findProperty("ossrh.password") as String? ?: ""
                }
            }
        }
    }

    extensions.configure<SigningExtension> {
        val signingKey = System.getenv("GPG_SIGNING_KEY") ?: project.findProperty("signing.key") as String?
        val signingPassword = System.getenv("GPG_SIGNING_PASSWORD") ?: project.findProperty("signing.password") as String?
        if (signingKey != null && signingPassword != null) {
            useInMemoryPgpKeys(signingKey, signingPassword)
            val publishing = extensions.getByType<PublishingExtension>()
            sign(publishing.publications["maven"])
        } else {
            isRequired = false
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

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}
