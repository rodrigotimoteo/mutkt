buildscript {
    repositories {
        mavenLocal()
        google()
        gradlePluginPortal()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
    }
}

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
        mavenLocal()
        google()
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

allprojects {
    group = "io.github.rodrigotimoteo"
    version = "0.3.0"
}

dependencies {
    dokka(project(":mutation-core"))
    dokka(project(":mutation-report"))
    dokka(project(":mutation-test-runner"))
    dokka(project(":mutation-sample"))
}

subprojects {
    if (name == "mutation-sample" || name == "mutation-sample-android" || name == "mutation-self-test") return@subprojects

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")
    apply(plugin = "org.jetbrains.kotlinx.kover")

    extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension> {
        excludeSourceSets {
            names("generated")
        }
    }

    apply(plugin = "org.jetbrains.dokka")
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
                }
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/rodrigotimoteo/mutkt")
                credentials {
                    username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String? ?: "rodrigotimoteo"
                    password = System.getenv("GH_PACKAGES_TOKEN") ?: System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.key") as String? ?: ""
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
            tasks.withType<AbstractPublishToMaven>().configureEach {
                val signingTasks = tasks.withType<Sign>()
                mustRunAfter(signingTasks)
            }
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
