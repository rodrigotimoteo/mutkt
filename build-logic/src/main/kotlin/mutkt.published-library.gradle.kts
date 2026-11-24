plugins {
    id("mutkt.kotlin-library")
    id("org.jetbrains.kotlinx.kover")
}

kotlin {
    explicitApi()
}

kover {
    reports {
        verify {
            rule {
                minBound(85)
            }
        }
    }
}
