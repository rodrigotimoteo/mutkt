package com.github.rodrigotimoteo.mutkt.asm

import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ACC_SYNTHETIC
import org.objectweb.asm.Opcodes.ACC_TRANSIENT
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.ARETURN
import org.objectweb.asm.Opcodes.ASTORE
import org.objectweb.asm.Opcodes.ATHROW
import org.objectweb.asm.Opcodes.BASTORE
import org.objectweb.asm.Opcodes.BIPUSH
import org.objectweb.asm.Opcodes.DUP
import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.ICONST_1
import org.objectweb.asm.Opcodes.ICONST_5
import org.objectweb.asm.Opcodes.IFNONNULL
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.Opcodes.MONITORENTER
import org.objectweb.asm.Opcodes.MONITOREXIT
import org.objectweb.asm.Opcodes.NEWARRAY
import org.objectweb.asm.Opcodes.POP
import org.objectweb.asm.Opcodes.PUTSTATIC
import org.objectweb.asm.Opcodes.SIPUSH
import org.objectweb.asm.Opcodes.T_BOOLEAN
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
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.VarInsnNode

/** Name of the static `boolean[]` field that holds a class's probes. */
internal const val PROBE_FIELD = "\$mutktProbes"
private const val PROBE_INIT = "\$mutktInit"
private const val PROBES_TYPE = "[Z"
private const val HIDDEN = ACC_PRIVATE or ACC_STATIC or ACC_SYNTHETIC

// One JVM-wide interned string guards every class's first probe; user code never locks it, unlike the class.
private const val PROBE_LOCK = "mutkt probe initialisation"

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
 * Adds the probe field and the accessor that creates its array of [size] on first use. Lazy like JaCoCo's, so it
 * does not depend on when `<clinit>` runs, and a class whose probes were never reached keeps a null field.
 */
internal fun ClassNode.addProbeArray(size: Int) {
    check(fields.none { it.name == PROBE_FIELD } && methods.none { it.name == PROBE_INIT }) {
        "$name already has probes"
    }
    fields.add(FieldNode(HIDDEN or ACC_TRANSIENT, PROBE_FIELD, PROBES_TYPE, null, null))
    val owner = name
    methods.add(MethodNode(HIDDEN, PROBE_INIT, "()$PROBES_TYPE", null, null).apply { probeInitCode(owner, size) })
}

// Double-checked: the unlocked read is safe without volatile because a racing reader at worst sees null and takes
// the lock, and a published array holds only defaults; the lock stops two threads each creating one and losing hits.
private fun MethodNode.probeInitCode(
    owner: String,
    size: Int,
) {
    val ready = LabelNode()
    instructions.apply {
        add(FieldInsnNode(GETSTATIC, owner, PROBE_FIELD, PROBES_TYPE))
        add(InsnNode(DUP))
        add(JumpInsnNode(IFNONNULL, ready))
        add(InsnNode(POP))
        add(lockedCreate(owner, size))
        add(ready)
        add(InsnNode(ARETURN))
    }
}

// Leaves the array on the stack; the catch-any handler releases the lock if allocation throws.
private fun MethodNode.lockedCreate(
    owner: String,
    size: Int,
): InsnList {
    val start = LabelNode()
    val end = LabelNode()
    val handler = LabelNode()
    val created = LabelNode()
    tryCatchBlocks.add(TryCatchBlockNode(start, end, handler, null))
    return InsnList().apply {
        add(LdcInsnNode(PROBE_LOCK))
        add(InsnNode(DUP))
        add(VarInsnNode(ASTORE, 0))
        add(InsnNode(MONITORENTER))
        add(start)
        add(FieldInsnNode(GETSTATIC, owner, PROBE_FIELD, PROBES_TYPE))
        add(InsnNode(DUP))
        add(JumpInsnNode(IFNONNULL, created))
        add(InsnNode(POP))
        add(pushInt(size))
        add(IntInsnNode(NEWARRAY, T_BOOLEAN))
        add(InsnNode(DUP))
        add(FieldInsnNode(PUTSTATIC, owner, PROBE_FIELD, PROBES_TYPE))
        add(created)
        add(VarInsnNode(ALOAD, 0))
        add(InsnNode(MONITOREXIT))
        add(end)
        add(InsnNode(ARETURN))
        add(handler)
        add(VarInsnNode(ALOAD, 0))
        add(InsnNode(MONITOREXIT))
        add(InsnNode(ATHROW))
    }
}
