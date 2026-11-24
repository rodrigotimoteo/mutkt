package com.github.rodrigotimoteo.mutkt.junit

import java.net.URL
import java.util.Enumeration

// Identity must match between the plugin's launcher and the project's engines; suite, reporting and the like
// stay project-side because a project may bring its own.
private val sharedPackages =
    listOf(
        "org.junit.platform.launcher.",
        "org.junit.platform.engine.",
        "org.junit.platform.commons.",
        "org.opentest4j.",
    )
private val sharedPaths = sharedPackages.map { it.replace('.', '/') }

// Not child-first and not parent-first: the Launcher API types must be the plugin's so the project's
// tests can be run through them, while every other plugin class (stdlib, ASM) stays invisible to the project.
internal class LauncherApiLoader(
    parent: ClassLoader,
    private val pluginLoader: ClassLoader,
) : ClassLoader(parent) {
    // resolve is ignored: this loader never defines classes, the delegate that does has already linked them.
    override fun loadClass(
        name: String,
        resolve: Boolean,
    ): Class<*> =
        if (sharedPackages.any(name::startsWith)) {
            pluginLoader.loadClass(name)
        } else {
            super.loadClass(name, resolve)
        }

    override fun getResource(name: String): URL? = source(name).getResource(name)

    override fun getResources(name: String): Enumeration<URL> = source(name).getResources(name)

    private fun source(name: String): ClassLoader = if (sharedPaths.any(name::startsWith)) pluginLoader else parent
}
