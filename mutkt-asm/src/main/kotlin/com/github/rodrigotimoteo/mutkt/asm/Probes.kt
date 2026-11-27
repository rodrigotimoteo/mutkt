package com.github.rodrigotimoteo.mutkt.asm

import org.objectweb.asm.Opcodes.AASTORE
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ACC_SYNTHETIC
import org.objectweb.asm.Opcodes.ACC_TRANSIENT
import org.objectweb.asm.Opcodes.ACONST_NULL
import org.objectweb.asm.Opcodes.ANEWARRAY
import org.objectweb.asm.Opcodes.ARETURN
import org.objectweb.asm.Opcodes.BASTORE
import org.objectweb.asm.Opcodes.BIPUSH
import org.objectweb.asm.Opcodes.CHECKCAST
import org.objectweb.asm.Opcodes.DUP
import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.ICONST_1
import org.objectweb.asm.Opcodes.ICONST_5
import org.objectweb.asm.Opcodes.IFNONNULL
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.Opcodes.INVOKEVIRTUAL
import org.objectweb.asm.Opcodes.POP
import org.objectweb.asm.Opcodes.PUTSTATIC
import org.objectweb.asm.Opcodes.SIPUSH
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode

/** Name of the static `boolean[]` field that holds a class's probes. */
internal const val PROBE_FIELD = "\$mutktProbes"
private const val PROBE_INIT = "\$mutktInit"
private const val PROBES_TYPE = "[Z"
private const val HIDDEN = ACC_PRIVATE or ACC_STATIC or ACC_SYNTHETIC

private const val LOADER = "java/lang/ClassLoader"
private const val CLASS = "java/lang/Class"
private const val STRING = "java/lang/String"
private const val INTEGER = "java/lang/Integer"
private const val OBJECT = "java/lang/Object"
private const val METHOD = "java/lang/reflect/Method"

/** Sets probe [index] of class [owner] right before each instruction in [at]; leaves the stack as it was. */
internal fun MethodNode.insertProbe(
    owner: String,
    at: List<AbstractInsnNode>,
    index: Int,
) = at.forEach { instructions.insertBefore(it, probe(owner, index)) }

private fun probe(
    owner: String,
    index: Int,
) = InsnList().apply {
    add(MethodInsnNode(INVOKESTATIC, owner, PROBE_INIT, "()$PROBES_TYPE", false))
    add(pushInt(index))
    add(InsnNode(ICONST_1))
    add(InsnNode(BASTORE))
}

private fun pushInt(value: Int): AbstractInsnNode =
    when (value) {
        in 0..ICONST_5 - ICONST_0 -> InsnNode(ICONST_0 + value)
        in Byte.MIN_VALUE..Byte.MAX_VALUE -> IntInsnNode(BIPUSH, value)
        in Short.MIN_VALUE..Short.MAX_VALUE -> IntInsnNode(SIPUSH, value)
        else -> LdcInsnNode(value)
    }

/**
 * Adds the probe field and the accessor that fetches its array of [size] from [registry] (a class on the system class
 * path) under [key] on first use. Lazy like JaCoCo's, so it does not depend on when `<clinit>` runs, and a class whose
 * probes were never reached keeps a null field. Through `java.lang.ClassLoader`, which every loader shares, so the
 * copies a sandbox loader (Robolectric's) defines again from the class file all hit one array.
 */
internal fun ClassNode.addProbeArray(
    registry: String,
    key: String,
    size: Int,
) {
    check(fields.none { it.name == PROBE_FIELD } && methods.none { it.name == PROBE_INIT }) {
        "$name already has probes"
    }
    fields.add(FieldNode(HIDDEN or ACC_TRANSIENT, PROBE_FIELD, PROBES_TYPE, null, null))
    val owner = name
    methods.add(
        MethodNode(HIDDEN, PROBE_INIT, "()$PROBES_TYPE", null, null).apply {
            instructions.add(probeInitCode(owner, fetch(registry, key, size)))
        },
    )
}

// The unlocked read is safe without volatile: a racing reader at worst sees null and fetches again, and the registry
// hands every caller for a class the same array, so no hit is lost.
private fun probeInitCode(
    owner: String,
    fetch: InsnList,
) = InsnList().apply {
    val ready = LabelNode()
    add(FieldInsnNode(GETSTATIC, owner, PROBE_FIELD, PROBES_TYPE))
    add(InsnNode(DUP))
    add(JumpInsnNode(IFNONNULL, ready))
    add(InsnNode(POP))
    add(fetch)
    add(InsnNode(DUP))
    add(FieldInsnNode(PUTSTATIC, owner, PROBE_FIELD, PROBES_TYPE))
    add(ready)
    add(InsnNode(ARETURN))
}

// ClassLoader.getSystemClassLoader().loadClass(registry).getMethod("probes", String, int).invoke(null, key, size).
// A registry that is missing or denied throws out of the probed method, failing the baseline on probed classes, which
// then falls back to running every mutant against the whole suite.
private fun fetch(
    registry: String,
    key: String,
    size: Int,
) = InsnList().apply {
    add(MethodInsnNode(INVOKESTATIC, LOADER, "getSystemClassLoader", "()L$LOADER;", false))
    add(LdcInsnNode(registry))
    add(MethodInsnNode(INVOKEVIRTUAL, LOADER, "loadClass", "(L$STRING;)L$CLASS;", false))
    add(LdcInsnNode("probes"))
    add(
        array(
            CLASS,
            listOf(LdcInsnNode(Type.getObjectType(STRING))),
            listOf(FieldInsnNode(GETSTATIC, INTEGER, "TYPE", "L$CLASS;")),
        ),
    )
    add(MethodInsnNode(INVOKEVIRTUAL, CLASS, "getMethod", "(L$STRING;[L$CLASS;)L$METHOD;", false))
    add(InsnNode(ACONST_NULL))
    add(
        array(
            OBJECT,
            listOf(LdcInsnNode(key)),
            listOf(pushInt(size), MethodInsnNode(INVOKESTATIC, INTEGER, "valueOf", "(I)L$INTEGER;", false)),
        ),
    )
    add(MethodInsnNode(INVOKEVIRTUAL, METHOD, "invoke", "(L$OBJECT;[L$OBJECT;)L$OBJECT;", false))
    add(TypeInsnNode(CHECKCAST, PROBES_TYPE))
}

// An array of [type] whose element i is what items[i] pushes.
private fun array(
    type: String,
    vararg items: List<AbstractInsnNode>,
) = InsnList().apply {
    add(pushInt(items.size))
    add(TypeInsnNode(ANEWARRAY, type))
    items.forEachIndexed { index, item ->
        add(InsnNode(DUP))
        add(pushInt(index))
        item.forEach(::add)
        add(InsnNode(AASTORE))
    }
}
