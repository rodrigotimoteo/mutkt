package com.github.rodrigotimoteo.mutkt.junit

import java.nio.file.Path

// Platform <- LauncherApiLoader <- MutantClassLoader(classpath + classes). One loader defines the dependencies and the
// project's classes, so a library's Class.forName and package scans see the project; a fresh one per run keeps
// library statics from carrying a mutant's classes into the next run.
internal fun projectLoader(
    classpath: List<Path>,
    pluginLoader: ClassLoader,
    classes: Map<String, ByteArray>,
    codeSources: Map<String, Path>,
): MutantClassLoader =
    MutantClassLoader(
        LauncherApiLoader(ClassLoader.getPlatformClassLoader(), pluginLoader),
        classes,
        classpath,
        codeSources,
    )
