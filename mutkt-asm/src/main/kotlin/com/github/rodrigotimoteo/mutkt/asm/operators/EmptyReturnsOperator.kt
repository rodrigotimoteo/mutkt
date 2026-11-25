package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.ClassInfo
import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Opcodes.ANEWARRAY
import org.objectweb.asm.Opcodes.ARETURN
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.Opcodes.NEWARRAY
import org.objectweb.asm.Opcodes.POP
import org.objectweb.asm.Opcodes.T_BOOLEAN
import org.objectweb.asm.Opcodes.T_BYTE
import org.objectweb.asm.Opcodes.T_CHAR
import org.objectweb.asm.Opcodes.T_DOUBLE
import org.objectweb.asm.Opcodes.T_FLOAT
import org.objectweb.asm.Opcodes.T_INT
import org.objectweb.asm.Opcodes.T_LONG
import org.objectweb.asm.Opcodes.T_SHORT
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TableSwitchInsnNode
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

/** Replaces a returned array, List, Set, Map or Collection with an empty one. */
internal object EmptyReturnsOperator : Operator {
    override val id = OperatorId("empty-returns")

    override fun sites(
        method: MethodNode,
        cls: ClassInfo,
    ): List<Site> {
        val returnType = Type.getReturnType(method.desc).descriptor
        return if (returnType.startsWith("[") || returnType in EMPTY_FACTORY) {
            val targets = jumpTargets(method)
            method.instructions
                .filter { it.opcode == ARETURN }
                .filterNot { returnsEmptyAlready(it, targets) }
                .map { Site(it, "areturn -> empty") }
        } else {
            emptyList()
        }
    }

    // The original ARETURN stays and returns the pushed empty value.
    override fun apply(
        method: MethodNode,
        site: Site,
    ) = method.instructions.insertBefore(
        site.instruction,
        InsnList().apply {
            add(InsnNode(POP))
            add(emptyValue(Type.getReturnType(method.desc).descriptor))
        },
    )

    private fun emptyValue(returnDesc: String): InsnList =
        InsnList().apply {
            val factory = EMPTY_FACTORY[returnDesc]
            if (factory != null) {
                // Collection uses emptyList, whose declared return type is List.
                val result = if (factory == "emptyList") "Ljava/util/List;" else returnDesc
                add(MethodInsnNode(INVOKESTATIC, "java/util/Collections", factory, "()$result", false))
            } else {
                val component = Type.getType(returnDesc.substring(1))
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

    private fun jumpTargets(method: MethodNode): Set<LabelNode> =
        buildSet {
            method.instructions.forEach {
                when (it) {
                    is JumpInsnNode -> add(it.label)
                    is TableSwitchInsnNode -> addAll(it.labels + it.dflt)
                    is LookupSwitchInsnNode -> addAll(it.labels + it.dflt)
                }
            }
            method.tryCatchBlocks.forEach { add(it.handler) }
        }

    // Line-number labels sit between the value and the return; a jump target or frame there is a merge point,
    // where the value may not be empty, so the site stays.
    private fun returnsEmptyAlready(
        ret: AbstractInsnNode,
        targets: Set<LabelNode>,
    ): Boolean {
        var previous = ret.previous
        while (previous is LineNumberNode ||
            (previous is LabelNode && previous !in targets)
        ) {
            previous = previous.previous
        }
        return when (previous) {
            is MethodInsnNode ->
                previous.owner in EMPTY_FACTORY_OWNERS &&
                    previous.name.startsWith("empty") &&
                    previous.desc.startsWith("()")
            is TypeInsnNode -> previous.opcode == ANEWARRAY && previous.previous?.opcode == ICONST_0
            is IntInsnNode -> previous.opcode == NEWARRAY && previous.previous?.opcode == ICONST_0
            else -> false
        }
    }
}
