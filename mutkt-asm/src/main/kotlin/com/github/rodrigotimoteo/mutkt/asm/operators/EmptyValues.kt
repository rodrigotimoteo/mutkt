package com.github.rodrigotimoteo.mutkt.asm.operators

import org.objectweb.asm.Opcodes.ANEWARRAY
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.Opcodes.NEWARRAY
import org.objectweb.asm.Opcodes.T_BOOLEAN
import org.objectweb.asm.Opcodes.T_BYTE
import org.objectweb.asm.Opcodes.T_CHAR
import org.objectweb.asm.Opcodes.T_DOUBLE
import org.objectweb.asm.Opcodes.T_FLOAT
import org.objectweb.asm.Opcodes.T_INT
import org.objectweb.asm.Opcodes.T_LONG
import org.objectweb.asm.Opcodes.T_SHORT
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.TypeInsnNode

// Kotlin List, Set, Map, Collection and their read-write variants all compile to these JVM types. Collection takes an
// empty list, which is assignable to it. Suspend functions return Object, so they never match.
private val EMPTY_FACTORY =
    mapOf(
        "Ljava/util/List;" to "emptyList",
        "Ljava/util/Collection;" to "emptyList",
        "Ljava/util/Set;" to "emptySet",
        "Ljava/util/Map;" to "emptyMap",
    )

private val EMPTY_FACTORY_OWNERS =
    setOf(
        "java/util/Collections",
        "kotlin/collections/CollectionsKt",
        "kotlin/collections/SetsKt",
        "kotlin/collections/MapsKt",
    )

private val PRIMITIVE_ARRAY =
    mapOf(
        "Z" to T_BOOLEAN,
        "C" to T_CHAR,
        "F" to T_FLOAT,
        "D" to T_DOUBLE,
        "B" to T_BYTE,
        "S" to T_SHORT,
        "I" to T_INT,
        "J" to T_LONG,
    )

/** True when [descriptor] is an array or a `java.util` List, Set, Map or Collection, which [emptyValue] can create. */
internal fun hasEmptyValue(descriptor: String) = descriptor.startsWith("[") || descriptor in EMPTY_FACTORY

/** A no-argument `empty*` factory of `java.util.Collections` or the Kotlin collection facades. */
internal fun MethodInsnNode.isEmptyFactoryCall() =
    owner in EMPTY_FACTORY_OWNERS && name.startsWith("empty") && desc.startsWith("()")

/** Pushes an empty value of [descriptor] (see [hasEmptyValue]): a shared empty collection or a new array. */
internal fun emptyValue(descriptor: String): InsnList =
    InsnList().apply {
        val factory = EMPTY_FACTORY[descriptor]
        if (factory != null) {
            // Collection uses emptyList, whose declared return type is List.
            val result = if (factory == "emptyList") "Ljava/util/List;" else descriptor
            add(MethodInsnNode(INVOKESTATIC, "java/util/Collections", factory, "()$result", false))
        } else {
            val component = Type.getType(descriptor.substring(1))
            val primitive = PRIMITIVE_ARRAY[component.descriptor]
            add(InsnNode(ICONST_0))
            add(
                if (primitive != null) {
                    IntInsnNode(NEWARRAY, primitive)
                } else {
                    TypeInsnNode(ANEWARRAY, component.internalName)
                },
            )
        }
    }
