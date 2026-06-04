plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.10" apply false
}

allprojects {
    group = "com.github.rodrigotimoteo"
    version = "0.1.0"

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

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
                    username = System.getenv("GITHUB_ACTOR") ?: "rodrigotimoteo"
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }

    extensions.configure<SigningExtension> {
        val signingKey = System.getenv("GPG_SIGNING_KEY")
        val signingPassword = System.getenv("GPG_SIGNING_PASSWORD")
        if (signingKey != null && signingPassword != null) {
            useInMemoryPgpKeys(signingKey, signingPassword)
        }
        val publishing = extensions.getByType<PublishingExtension>()
        sign(publishing.publications["maven"])
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "21"
            freeCompilerArgs += listOf(
                "-Xopt-in=kotlin.RequiresOptIn",
                "-Xjsr305=strict"
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
