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

/** Calls static [method] of [className] in a fresh loader; overloads unsupported (first name+arity wins). */
fun loadAndInvoke(
    classes: Map<String, ByteArray>,
    className: String,
    method: String,
    vararg args: Any?,
): Any? {
    val type = BytesClassLoader(classes).loadClass(className)
    val target =
        type.methods.firstOrNull { it.name == method && it.parameterCount == args.size }
            ?: throw NoSuchMethodException("$className.$method/${args.size}")
    return try {
        target.invoke(null, *args)
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }
}
