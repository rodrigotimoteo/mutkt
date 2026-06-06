plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.dokka)
    alias(libs.plugins.owasp.dependencycheck)
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
    version = "0.1.1"

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

// Dokka V2 multi-module aggregation: include each subproject's docs in the
// root aggregated output. See https://kotlinlang.org/docs/dokka-migration.html
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

            // Configure all publications (including plugin marker) with required POM metadata
            withType<MavenPublication>().configureEach {
                pom {
                    name.set(project.name)
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
            sign(publishing.publications)
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

// OWASP dependency-check configuration.
// Use the NVD data feed (no API key required). The public REST API
// is heavily rate-limited and CI runs hit 429s without a key. The
// data feed is a single bulk JSON download per year, cached locally
// and reused for `validForHours`. Optionally set NVD_API_KEY (free
// at https://nvd.nist.gov/developers/request-an-api-key) to switch
// back to the REST API for freshest data.
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
