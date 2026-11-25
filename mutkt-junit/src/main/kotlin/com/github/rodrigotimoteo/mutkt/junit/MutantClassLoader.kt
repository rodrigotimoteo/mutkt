package com.github.rodrigotimoteo.mutkt.junit

import java.io.ByteArrayInputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.util.Collections
import java.util.Enumeration

// Only mapped names are child-first: classes linked from the parent keep seeing the parent's originals,
// so whoever builds the chain must load the test classes through a loader that resolves to this one.
internal class MutantClassLoader(
    parent: ClassLoader,
    mutatedClasses: Map<String, ByteArray>,
) : ClassLoader(parent),
    AutoCloseable {
    private val mutatedClasses = mutatedClasses.toMap()

    @Volatile private var closed = false

    // Not registered parallel-capable, so getClassLoadingLock is loader-wide.
    override fun loadClass(
        name: String,
        resolve: Boolean,
    ): Class<*> {
        val bytes = mutatedClasses[name] ?: return super.loadClass(name, resolve)
        check(!closed) { "Class loader is closed, cannot load $name" }
        val type =
            synchronized(getClassLoadingLock(name)) {
                findLoadedClass(name) ?: defineClass(name, bytes, 0, bytes.size)
            }
        if (resolve) resolveClass(type)
        return type
    }

    // A class file a test reads as a resource must be the definition loadClass gives, never a parent copy.
    private val classBytes =
        object : URLStreamHandler() {
            override fun openConnection(url: URL): URLConnection =
                object : URLConnection(url) {
                    override fun connect() = Unit

                    private val bytes = mutatedClasses.getValue(classNameOf(url.path))

                    override fun getInputStream() = ByteArrayInputStream(bytes)

                    override fun getContentLength() = bytes.size

                    override fun getContentLengthLong() = bytes.size.toLong()
                }
        }

    private fun classNameOf(resource: String) = resource.removePrefix("/").removeSuffix(CLASS_SUFFIX).replace('/', '.')

    // A leading slash is not a valid loader resource name; it misses, like in any ClassLoader.
    private fun isMapped(resource: String) =
        !resource.startsWith("/") && resource.endsWith(CLASS_SUFFIX) && classNameOf(resource) in mutatedClasses

    // URL(..., handler) is deprecated since JDK 20 but URL.of does not exist on 17.
    @Suppress("DEPRECATION")
    override fun getResource(name: String): URL? =
        if (isMapped(name)) URL("mutkt-class", null, -1, "/$name", classBytes) else super.getResource(name)

    override fun getResources(name: String): Enumeration<URL> =
        if (isMapped(name)) Collections.enumeration(listOfNotNull(getResource(name))) else super.getResources(name)

    override fun close() {
        closed = true
    }

    private companion object {
        const val CLASS_SUFFIX = ".class"
    }
}
