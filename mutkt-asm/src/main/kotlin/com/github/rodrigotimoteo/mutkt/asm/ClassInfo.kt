package com.github.rodrigotimoteo.mutkt.asm

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes.ACC_ENUM
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

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

    // Lookups repeat for every call site of a class, so headers and answers are kept for this instance's lifetime.
    private val headers = mutableMapOf<String, ClassNode?>()
    private val nonNullCalls = mutableMapOf<Triple<String, String, String>, Boolean>()

    /** True when [method] of this class is a Kotlin member declared non-null, so it can never return null. */
    fun declaresNonNull(method: MethodNode): Boolean = isKotlin && method.returnsNonNull()

    /** True when the called [owner].[name] [desc], or the supertype method it resolves to, is Kotlin non-null. */
    fun returnsNonNull(
        owner: String,
        name: String,
        desc: String,
    ): Boolean =
        nonNullCalls.getOrPut(Triple(owner, name, desc)) {
            (defaultBridgeTargets(owner, name, desc) + (name to desc))
                .firstNotNullOfOrNull { (method, descriptor) -> declarationOf(::headerOf, owner, method, descriptor) }
                ?.let { (declaring, method) -> declaring.isKotlin() && method.returnsNonNull() } == true
        }

    private fun headerOf(type: String): ClassNode? =
        if (type in headers) headers[type] else hierarchy.bytesOrNull(type)?.let(::header).also { headers[type] = it }

    // The hierarchy is a lookup function, so identity is the three plain facts.
    override fun equals(other: Any?) =
        other is ClassInfo &&
            internalName == other.internalName &&
            isKotlin == other.isKotlin &&
            superName == other.superName

    override fun hashCode() = listOf(internalName, isKotlin, superName).hashCode()

    override fun toString() = "ClassInfo(internalName=$internalName, isKotlin=$isKotlin, superName=$superName)"
}

internal fun ClassNode.info(hierarchy: Hierarchy = NoClasses) = ClassInfo(name, isKotlin(), superName, hierarchy)
