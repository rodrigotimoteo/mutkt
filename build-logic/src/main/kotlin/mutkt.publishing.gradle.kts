plugins {
    id("org.jetbrains.kotlin.jvm")
    `maven-publish`
    signing
}

val repoUrl = "https://github.com/rodrigotimoteo/mutkt"
val centralStagingApi = "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"

// Central requires both jars; the Kotlin-only javadoc jar is empty on purpose (no Java sources to document).
java {
    withSourcesJar()
    withJavadocJar()
}

// Same inputs, same bytes: a retry can compare against an already published version.
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// java-gradle-plugin publishes its own jar and the plugin marker, so only plain libraries get mavenJava.
var isGradlePlugin = false
pluginManager.withPlugin("java-gradle-plugin") { isGradlePlugin = true }
afterEvaluate {
    if (!isGradlePlugin) {
        publishing.publications.create<MavenPublication>("mavenJava") { from(components["java"]) }
    }
}

publishing {
    publications {
        // Convention values only: the marker publication brings its own name and description.
        withType<MavenPublication>().configureEach {
            pom {
                name.convention(provider { "MutKt " + project.name.removePrefix("mutkt-").replaceFirstChar(Char::uppercase) })
                description.convention(provider { project.description })
                url = repoUrl
                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                developers {
                    developer {
                        id = "rodrigotimoteo"
                        name = "Rodrigo Timoteo"
                        url = "https://github.com/rodrigotimoteo"
                    }
                }
                scm {
                    connection = "scm:git:$repoUrl.git"
                    developerConnection = "scm:git:ssh://git@github.com/rodrigotimoteo/mutkt.git"
                    url = repoUrl
                }
            }
        }
    }
    repositories {
        // Credentials come from the centralUsername and centralPassword properties; -Pmutkt.publishUrl points rehearsals elsewhere.
        maven {
            name = "central"
            val target = providers.gradleProperty("mutkt.publishUrl").getOrElse(centralStagingApi)
            url = uri(target)
            if (!target.startsWith("file:")) credentials(PasswordCredentials::class)
        }
    }
}

// Signing needs keys only when they are supplied, so local builds stay unsigned.
signing {
    val key = providers.gradleProperty("signingInMemoryKey")
    if (key.isPresent) {
        useInMemoryPgpKeys(key.get(), providers.gradleProperty("signingInMemoryKeyPassword").getOrElse(""))
        sign(publishing.publications)
    }
}
