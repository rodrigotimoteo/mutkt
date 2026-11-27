package com.github.rodrigotimoteo.mutkt.testing

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.reflect.InvocationTargetException

private const val CLASS_SUFFIX = ".class"

private class BytesClassLoader(
    parent: ClassLoader,
    private val classes: (String) -> ByteArray?,
) : ClassLoader(parent) {
    // Child-first for the given names, so a class shadows a same-named one on the test classpath.
    override fun loadClass(
        name: String,
        resolve: Boolean,
    ): Class<*> =
        synchronized(getClassLoadingLock(name)) {
            val bytes = classes(name) ?: return super.loadClass(name, resolve)
            findLoadedClass(name) ?: defineClass(name, bytes, 0, bytes.size)
        }

    override fun getResourceAsStream(name: String): InputStream? =
        name
            .takeIf { it.endsWith(CLASS_SUFFIX) }
            ?.let { classes(it.removeSuffix(CLASS_SUFFIX).replace('/', '.')) }
            ?.let(::ByteArrayInputStream)
            ?: super.getResourceAsStream(name)
}

/** [classes] in one fresh loader, so calls and reads see the same static state. */
class IsolatedClasses private constructor(
    private val loader: ClassLoader,
    private val names: Set<String>,
) {
    constructor(classes: Map<String, ByteArray>) :
        this(BytesClassLoader(BytesClassLoader::class.java.classLoader, classes::get), classes.keys)

    /** Calls static [method] of [className]; overloads unsupported (first name+arity wins). */
    fun invoke(
        className: String,
        method: String,
        vararg args: Any?,
    ): Any? {
        val target =
            loader.loadClass(className).methods.firstOrNull { it.name == method && it.parameterCount == args.size }
                ?: throw NoSuchMethodException("$className.$method/${args.size}")
        return try {
            target.invoke(null, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    /** The class [className] as this loader defines it. */
    fun type(className: String): Class<*> = loader.loadClass(className)

    /** The static `boolean[]` [field] of [className], e.g. a coverage probe array; null while unset. */
    fun probes(
        className: String,
        field: String,
    ): BooleanArray? =
        loader
            .loadClass(className)
            .getDeclaredField(field)
            .apply { isAccessible = true }
            .get(null) as BooleanArray?

    /** The same classes defined again from this loader's class file resources, as Robolectric's sandbox does. */
    fun sandbox(): IsolatedClasses = IsolatedClasses(sandboxLoader(loader, names), names)
}

/**
 * A loader that defines [names] again from the class file resources of [source], child-first, and delegates the rest
 * to it, like Robolectric's sandbox loader.
 */
fun sandboxLoader(
    source: ClassLoader,
    names: Set<String>,
): ClassLoader = BytesClassLoader(source) { name -> if (name in names) source.classFile(name) else null }

private fun ClassLoader.classFile(name: String): ByteArray {
    val path = name.replace('.', '/') + CLASS_SUFFIX
    return checkNotNull(getResourceAsStream(path)) { "No class file for $name" }.use { it.readBytes() }
}

/** Calls static [method] of [className] in a fresh loader; overloads unsupported (first name+arity wins). */
fun loadAndInvoke(
    classes: Map<String, ByteArray>,
    className: String,
    method: String,
    vararg args: Any?,
): Any? = IsolatedClasses(classes).invoke(className, method, *args)
