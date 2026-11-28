plugins {
    id("mutkt.publishing")
    id("org.jetbrains.dokka")
}

// Dokka HTML fills the published javadoc jar. Its own jar task keeps Dokka, which resolves from the network, out of assemble.
val dokkaJavadocJar =
    tasks.register<Jar>("dokkaJavadocJar") {
        archiveClassifier = "javadoc"
        destinationDirectory = layout.buildDirectory.dir("dokka-javadoc")
        from(tasks.named("dokkaGeneratePublicationHtml"))
    }

configurations.named("javadocElements") {
    outgoing.artifacts.clear()
    outgoing.artifact(dokkaJavadocJar)
}

// The empty javadoc jar is no longer published; assemble still depends on it, so it is skipped rather than removed.
tasks.named("javadocJar") { enabled = false }
