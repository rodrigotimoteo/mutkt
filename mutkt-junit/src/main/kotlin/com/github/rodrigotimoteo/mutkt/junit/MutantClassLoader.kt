package com.github.rodrigotimoteo.mutkt.junit

import java.io.ByteArrayInputStream
import java.net.URL
import java.net.URLClassLoader
import java.net.URLConnection
import java.net.URLStreamHandler
import java.nio.file.Files
import java.nio.file.Path
import java.security.CodeSigner
import java.security.CodeSource
import java.util.Collections
import java.util.Enumeration

// Mapped names are child-first; [classpath] entries are searched after the parent. Classes linked from the parent
// keep seeing the parent's originals, so every class that must see a mapped one has to be defined here too.
// A package scan (getResources("a/b/")) lists the class directories on [classpath] and reads the original class files
// there, not the mapped bytes. A mapped class in [codeSources] reports that location as its code source.
internal class MutantClassLoader(
    parent: ClassLoader,
    mutatedClasses: Map<String, ByteArray>,
    classpath: List<Path> = emptyList(),
    private val codeSources: Map<String, Path> = emptyMap(),
) : URLClassLoader(classpath.map { it.toUri().toURL() }.toTypedArray(), parent) {
    private val mutatedClasses = mutatedClasses.toMap()
    private val classDirs = classpath.filter(Files::isDirectory)

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
                findLoadedClass(name) ?: defineClass(name, bytes, 0, bytes.size, codeSourceOf(name))
            }
        if (resolve) resolveClass(type)
        return type
    }

    // Where the original was built, else the class directory holding it, as a URLClassLoader would report it: tools
    // find sources from it (strikt's filepeek, Spring). SecureClassLoader keeps one ProtectionDomain per CodeSource.
    private fun codeSourceOf(name: String): CodeSource? {
        val file = name.replace('.', '/') + CLASS_SUFFIX
        val location =
            codeSources[name]
                ?: classDirs.firstOrNull { Files.isRegularFile(it.resolve(file)) }
                ?: return null
        return CodeSource(location.toUri().toURL(), null as Array<CodeSigner>?)
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
        super.close()
    }

    private companion object {
        const val CLASS_SUFFIX = ".class"
    }
}
