package com.github.rodrigotimoteo.mutkt.junit

import java.net.URLClassLoader
import java.nio.file.Path

// Platform <- LauncherApiLoader <- URLClassLoader(classpath); the caller parents a MutantClassLoader on the result.
internal fun projectLoader(
    classpath: List<Path>,
    pluginLoader: ClassLoader,
): URLClassLoader =
    URLClassLoader(
        classpath.map { it.toUri().toURL() }.toTypedArray(),
        LauncherApiLoader(ClassLoader.getPlatformClassLoader(), pluginLoader),
    )
