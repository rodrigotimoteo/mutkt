plugins {
    id("mutkt.quality")
}

// The included build is not part of the root project's source sets.
tasks.named("ktlintCheck") { dependsOn(gradle.includedBuild("build-logic").task(":ktlintCheck")) }
tasks.named("ktlintFormat") { dependsOn(gradle.includedBuild("build-logic").task(":ktlintFormat")) }
