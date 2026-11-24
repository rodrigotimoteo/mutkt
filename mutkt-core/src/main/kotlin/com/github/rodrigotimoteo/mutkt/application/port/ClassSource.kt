package com.github.rodrigotimoteo.mutkt.application.port

/** Supplies the compiled classes of the project under test. */
public interface ClassSource {
    public fun classes(): List<ClassFile>
}
