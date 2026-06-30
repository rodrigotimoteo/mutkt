plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.dokka)
    alias(libs.plugins.owasp.dependencycheck)
}

allprojects {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    group = "io.github.rodrigotimoteo"
    version = "0.3.1"
}

dependencies {
    dokka(project(":mutation-core"))
    dokka(project(":mutation-report"))
    dokka(project(":mutation-test-runner"))
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")

    if (name == "mutation-sample" || name == "mutation-sample-android" || name == "mutation-self-test") return@subprojects

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")
    apply(plugin = "org.jetbrains.kotlinx.kover")

    // POM metadata for every Maven publication in every published subproject
    // (mutation-core / mutation-gradle-plugin / mutation-report /
    // mutation-test-runner + the auto-generated plugin marker). Central
    // Portal rejects uploads without licenses / developers / scm — see
    // https://central.sonatype.com/publishing/requirements.
    extensions.configure<PublishingExtension> {
        publications {
            withType<MavenPublication>().configureEach {
                pom {
                    // Licenses / developers / scm are added in the
                    // subprojects PublishingExtension block above
                    // (so all MavenPublication instances in subprojects
                    // get them, including plugin markers).
                }
            }
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension> {
        currentProject {
            sources {
                excludedSourceSets.addAll("generated")
            }
        }
    }

    // Enforce 85% line coverage per published module. mutation-core /
    // mutation-gradle-plugin / mutation-report / mutation-test-runner are
    // published; the *sample* / *self-test* modules are excluded above.
    //
    // Kover 0.9.0 DSL: `reports.verify.rule.minBound(85)` expresses the
    // minimum allowed line coverage as a plain Int. The block runs
    // inside `subprojects { ... }` so the rule applies per-subproject
    // (not aggregated across the whole build).
    extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension> {
        reports {
            verify {
                rule {
                    minBound(85)
                }
            }
        }
    }

    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
                }
            }

            withType<MavenPublication>().configureEach {
                pom {
                    name.set(project.name)
                    description.set("Kotlin Mutation Testing - PITest-style for Kotlin/JVM")
                    url.set("https://github.com/rodrigotimoteo/mutkt")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    developers {
                        developer {
                            id.set("rodrigotimoteo")
                            name.set("Rodrigo Timoteo")
                            email.set("rodrigotimoteo@example.com")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/rodrigotimoteo/mutkt.git")
                        developerConnection.set("scm:git:ssh://git@github.com/rodrigotimoteo/mutkt.git")
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
            // Sonatype Central Portal (post-OSSRH). v0.3.0 publish used
            // the same staging URL via plain `maven-publish` rather than
            // gradle-nexus-publish-plugin. We keep the same approach
            // because the plugin's `publishMavenPublicationTo*` task
            // triggers a Gradle 8.10 strict task-dependency validation
            // error against the sign tasks of plugin-marker publications
            // in subprojects that apply `java-gradle-plugin`.
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

            // Gradle 8.10 strict task-dependency validation rejects the
            // implicit dependency that `AbstractPublishToMaven` has on
            // its corresponding sign task's outputs. v0.3.0 used
            // `mustRunAfter` to declare the ordering edge explicitly.
            // This silences the validation error and ensures .asc files
            // are written before the publish task reads them.
            tasks.withType<org.gradle.api.publish.maven.tasks.AbstractPublishToMaven>().configureEach {
                val signingTasks = tasks.withType<org.gradle.plugins.signing.Sign>()
                mustRunAfter(signingTasks)
            }
        } else {
            isRequired = false
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(
                org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.jvmTarget.get()),
            )
            freeCompilerArgs.addAll(
                listOf(
                    "-opt-in=kotlin.RequiresOptIn",
                    "-Xjsr305=strict",
                ),
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
        datafeedStartYear.set(2002)
    }
}
