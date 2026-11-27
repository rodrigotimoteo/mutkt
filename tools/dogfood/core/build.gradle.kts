plugins {
    id("mutkt.kotlin-library")
    id("io.github.rodrigotimoteo.mutkt")
}

mutkt {
    targetClasses.add("com.github.rodrigotimoteo.mutkt.**")
    // Ports are contracts and data carriers that only the adapters read; their tests live in the adapter modules,
    // which this run does not execute, so every getter would survive here.
    excludedClasses.add("com.github.rodrigotimoteo.mutkt.application.port.**")
    failBelowScore = 75
}
