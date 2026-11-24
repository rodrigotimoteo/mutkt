package com.github.rodrigotimoteo.mutkt.junit

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

    override fun close() {
        closed = true
    }
}
