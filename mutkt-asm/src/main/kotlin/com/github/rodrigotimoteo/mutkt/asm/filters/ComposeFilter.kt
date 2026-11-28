package com.github.rodrigotimoteo.mutkt.asm.filters

import com.github.rodrigotimoteo.mutkt.asm.SiteFilter
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.BIPUSH
import org.objectweb.asm.Opcodes.DUP
import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.Opcodes.GOTO
import org.objectweb.asm.Opcodes.IAND
import org.objectweb.asm.Opcodes.ICONST_5
import org.objectweb.asm.Opcodes.ICONST_M1
import org.objectweb.asm.Opcodes.IFEQ
import org.objectweb.asm.Opcodes.IFLE
import org.objectweb.asm.Opcodes.IFNE
import org.objectweb.asm.Opcodes.IF_ICMPEQ
import org.objectweb.asm.Opcodes.IF_ICMPLE
import org.objectweb.asm.Opcodes.ILOAD
import org.objectweb.asm.Opcodes.IOR
import org.objectweb.asm.Opcodes.ISHL
import org.objectweb.asm.Opcodes.ISHR
import org.objectweb.asm.Opcodes.ISTORE
import org.objectweb.asm.Opcodes.IUSHR
import org.objectweb.asm.Opcodes.IXOR
import org.objectweb.asm.Opcodes.SIPUSH
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode

private const val RUNTIME = "androidx/compose/runtime/"
private const val COMPOSER = "L${RUNTIME}Composer;"
private val BIT_OPS = setOf(IAND, IOR, IXOR, ISHL, ISHR, IUSHR)

// Runtime classes only plugin output calls; a user's `remember` or `LocalX.current` reaches them through inlining.
private val PLUGIN_OWNERS =
    listOf(
        "Composer",
        "Composer\$Companion",
        "ComposerKt",
        "ScopeUpdateScope",
        "RecomposeScopeImplKt",
        "internal/ComposableLambdaKt",
    ).map { RUNTIME + it }.toSet()

/**
 * In a method that takes a `Composer` (a `@Composable` function or lambda), the code the Compose compiler plugin
 * inserts: calls on the runtime API it targets (groups, `changed`, `getSkipping`, tracing, `updateScope`), the jumps
 * that test their results, the jumps that test `$changed`/`$default`/`$dirty` bits, and the `remember` cache
 * around the user's calculation. The restart lambda generated for each restartable function is noise as a whole.
 * Bit operations and int constants are no operator's sites, so only the jumps that consume them are listed.
 * No debug info needed.
 */
internal object ComposeFilter : SiteFilter {
    override fun noise(
        className: String,
        method: MethodNode,
    ): Set<AbstractInsnNode> =
        when {
            composerIndex(method) < 0 -> emptySet()
            isRestartLambda(method) -> method.instructions.toSet()
            else -> pluginCode(method)
        }

    private fun pluginCode(method: MethodNode): Set<AbstractInsnNode> {
        val slots = bitSlots(method)
        val jumps =
            method.instructions
                .filterIsInstance<JumpInsnNode>()
                .filter { it.opcode != GOTO && (testsPluginCall(it) || testsBits(it, slots) || testsInvalid(it)) }
        return (method.instructions.filter(::isPluginCall) + jumps).toSet()
    }

    // `Greeting$lambda$1(name, count, $changed, $composer, $force)` re-invokes Greeting with updateChangedFlags.
    private fun isRestartLambda(method: MethodNode) =
        method.access and ACC_STATIC != 0 &&
            method.desc.endsWith("${COMPOSER}I)Lkotlin/Unit;") &&
            method.instructions.any { isPluginCall(it) && (it as MethodInsnNode).name == "updateChangedFlags" }

    private fun isPluginCall(insn: AbstractInsnNode?) = insn is MethodInsnNode && insn.owner in PLUGIN_OWNERS

    // `changed(x)`, `getSkipping()`, `isTraceInProgress()`, `=== Composer.Empty`, DUP; IFNULL of `endRestartGroup()`.
    private fun testsPluginCall(jump: JumpInsnNode): Boolean {
        val top = realBefore(jump)
        return isPluginCall(if (top?.opcode == DUP) realBefore(top) else top)
    }

    // The int parameters after the Composer (`$changed`, `$default`, `$changed1`...) and their copies (`$dirty`).
    private fun bitSlots(method: MethodNode): Set<Int> {
        val types = Type.getArgumentTypes(method.desc)
        val first = if (method.access and ACC_STATIC != 0) 0 else 1
        val slots = types.runningFold(first) { slot, type -> slot + type.size }
        val params = slots.subList(composerIndex(method) + 1, types.size).toSet()
        val copies =
            method.instructions
                .filterIsInstance<VarInsnNode>()
                .filter { it.opcode == ISTORE && (realBefore(it) as? VarInsnNode)?.isLoadOf(params) == true }
                .map { it.`var` }
        return params + copies
    }

    // `ILOAD $dirty; BIPUSH 19; IAND; BIPUSH 18; IF_ICMPNE`: operands made of bit slots, int constants and bit ops.
    private fun testsBits(
        jump: JumpInsnNode,
        slots: Set<Int>,
    ): Boolean {
        var need =
            when (jump.opcode) {
                in IFEQ..IFLE -> 1
                in IF_ICMPEQ..IF_ICMPLE -> 2
                else -> 0
            }
        val operands =
            generateSequence(realBefore(jump), ::realBefore)
                .takeWhile { need > 0 && isBitTerm(it, slots) }
                .onEach { need += if (it.opcode in BIT_OPS) 1 else -1 }
                .toList()
        return need == 0 && operands.any { it is VarInsnNode }
    }

    /**
     * `remember(keys) { calc }` becomes `if (invalid || (value = rememberedValue()) === Composer.Empty)`, where
     * `invalid` ORs the `changed` results of the keys into a local (constant false without keys). This is the jump on
     * `invalid`: it falls through to the `Composer.Empty` comparison, which [testsPluginCall] already covers.
     * With debug info the inlined-body filter drops part of this cache too; the shape match also works without.
     */
    private fun testsInvalid(jump: JumpInsnNode): Boolean {
        val load = realAfter(jump)
        val empty = load?.let(::realAfter)
        return (jump.opcode == IFEQ || jump.opcode == IFNE) &&
            load?.opcode == ALOAD &&
            empty is FieldInsnNode &&
            empty.opcode == GETSTATIC &&
            empty.owner == RUNTIME + "Composer" &&
            empty.name == "Companion"
    }
}

private fun composerIndex(method: MethodNode) =
    Type.getArgumentTypes(method.desc).indexOfFirst {
        it.descriptor ==
            COMPOSER
    }

private fun VarInsnNode.isLoadOf(slots: Set<Int>) = opcode == ILOAD && `var` in slots

private fun isBitTerm(
    insn: AbstractInsnNode,
    slots: Set<Int>,
) = (insn is VarInsnNode && insn.isLoadOf(slots)) || isIntConstant(insn) || insn.opcode in BIT_OPS

private fun isIntConstant(insn: AbstractInsnNode) =
    insn.opcode in ICONST_M1..ICONST_5 ||
        insn.opcode == BIPUSH ||
        insn.opcode == SIPUSH ||
        (insn is LdcInsnNode && insn.cst is Int)
