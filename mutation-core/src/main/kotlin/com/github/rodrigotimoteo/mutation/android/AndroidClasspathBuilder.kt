package com.github.rodrigotimoteo.mutation.android

import java.io.File

/**
 * Builds a complete classpath for Android projects by extracting classes.jar from
 * AAR files and appending the android.jar stub for the Android SDK API surface.
 */
class AndroidClasspathBuilder(
    private val aarExtractor: AarExtractor,
    private val androidJar: File?,
) {
    fun build(runtimeClasspath: List<File>): List<File> {
        val aarJars =
            runtimeClasspath
                .filter { it.extension == "aar" }
                .map { aarExtractor.extractClassesJar(it) }
        val regularJars = runtimeClasspath.filter { it.extension != "aar" }
        return regularJars + aarJars + listOfNotNull(androidJar)
    }
}
