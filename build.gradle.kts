plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.dokka)
    alias(libs.plugins.owasp.dependencycheck)
    alias(libs.plugins.nexus.publish)
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
                    // Licenses / developers / scm are configured in
                    // `subprojects { PublishingExtension ... }` above so
                    // every published module + plugin marker gets them.
                    // (Configured in subprojects to avoid duplication.)
                }
            }
        }
    }

    // Make every Sonatype publish task explicitly depend on its
    // matching sign task. Gradle 8.10 strict validation rejects the
    // implicit dependency on `sign<MavenPublication>` outputs that
    // gradle-nexus-publish-plugin's publish tasks create. Without
    // this, subprojects that apply `java-gradle-plugin` (i.e.
    // mutation-gradle-plugin, which has the auto-generated
    // `pluginMaven` publication) fail configuration with
    // "uses output of task ... without declaring an explicit or
    // implicit dependency".
    tasks
        .matching { it.name.startsWith("publish") && it.name.endsWith("PublicationToSonatypeRepository") }
        .configureEach {
            val publishTaskName = name
            val signTaskName =
                publishTaskName
                    .removePrefix("publish")
                    .replace("PublicationToSonatypeRepository", "Publication")
            val signTask = tasks.findByName("sign$signTaskName")
            if (signTask != null) {
                dependsOn(signTask)
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

nexusPublishing {
    packageGroup.set("io.github.rodrigotimoteo")
    repositories {
        sonatype {
            // Sonatype Central Portal (replaces legacy s01.oss.sonatype.org,
            // sunset 2025-06-30). gradle-nexus-publish-plugin 2.0.0+ uses
            // these URLs to publish + close staging in one shot.
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            username.set(System.getenv("SONATYPE_USERNAME") ?: project.findProperty("sonatype.username") as String?)
            password.set(System.getenv("SONATYPE_PASSWORD") ?: project.findProperty("sonatype.password") as String?)
        }
    }
}
