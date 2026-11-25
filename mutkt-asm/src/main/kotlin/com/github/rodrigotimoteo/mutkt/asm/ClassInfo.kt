package com.github.rodrigotimoteo.mutkt.asm

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes.ACC_ENUM
import org.objectweb.asm.tree.ClassNode

/** What an [Operator] may know about the class that owns the method it inspects, and about the types it uses. */
internal class ClassInfo(
    val internalName: String,
    val isKotlin: Boolean,
    val superName: String?,
    private val hierarchy: Hierarchy = NoClasses,
) {
    /** True when [type] is an enum class; an unknown class is not assumed to be one. */
    fun isEnum(type: String): Boolean =
        type == "java/lang/Enum" ||
            hierarchy.bytesOrNull(type)?.let { ClassReader(it).access and ACC_ENUM != 0 } == true

    // The hierarchy is a lookup function, so identity is the three plain facts.
    override fun equals(other: Any?) =
        other is ClassInfo &&
            internalName == other.internalName &&
            isKotlin == other.isKotlin &&
            superName == other.superName

    override fun hashCode() = listOf(internalName, isKotlin, superName).hashCode()

    override fun toString() = "ClassInfo(internalName=$internalName, isKotlin=$isKotlin, superName=$superName)"
}

// kotlin.Metadata has runtime retention; javac output never carries it.
internal fun ClassNode.info(hierarchy: Hierarchy = NoClasses) =
    ClassInfo(name, visibleAnnotations.orEmpty().any { it.desc == "Lkotlin/Metadata;" }, superName, hierarchy)
