package com.github.rodrigotimoteo.mutkt.testing

import java.lang.reflect.InvocationTargetException

private class BytesClassLoader(
    private val classes: Map<String, ByteArray>,
) : ClassLoader(BytesClassLoader::class.java.classLoader) {
    // Child-first for the given names, so a class shadows a same-named one on the test classpath.
    override fun loadClass(
        name: String,
        resolve: Boolean,
    ): Class<*> =
        synchronized(getClassLoadingLock(name)) {
            val bytes = classes[name] ?: return super.loadClass(name, resolve)
            findLoadedClass(name) ?: defineClass(name, bytes, 0, bytes.size)
        }
}

/** [classes] in one fresh loader, so calls and reads see the same static state. */
class IsolatedClasses(
    classes: Map<String, ByteArray>,
) {
    private val loader = BytesClassLoader(classes)

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
}

/** Calls static [method] of [className] in a fresh loader; overloads unsupported (first name+arity wins). */
fun loadAndInvoke(
    classes: Map<String, ByteArray>,
    className: String,
    method: String,
    vararg args: Any?,
): Any? = IsolatedClasses(classes).invoke(className, method, *args)
