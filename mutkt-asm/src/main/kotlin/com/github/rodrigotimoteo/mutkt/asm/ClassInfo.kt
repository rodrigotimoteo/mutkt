package com.github.rodrigotimoteo.mutkt.asm

import org.objectweb.asm.tree.ClassNode

/** What an [Operator] may know about the class that owns the method it inspects. */
internal data class ClassInfo(
    val internalName: String,
    val isKotlin: Boolean,
    val superName: String?,
)

// kotlin.Metadata has runtime retention; javac output never carries it.
internal fun ClassNode.info() =
    ClassInfo(name, visibleAnnotations.orEmpty().any { it.desc == "Lkotlin/Metadata;" }, superName)
