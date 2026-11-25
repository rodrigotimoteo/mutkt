plugins {
    id("mutkt.kotlin-library")
    id("io.github.rodrigotimoteo.mutkt")
}

mutkt {
    targetClasses.add("com.github.rodrigotimoteo.mutkt.**")
    failBelowScore = 75
}
