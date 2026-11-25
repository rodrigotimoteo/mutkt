package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Opcodes.IFNONNULL
import org.objectweb.asm.Opcodes.IFNULL
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

private const val INTRINSICS = "kotlin/jvm/internal/Intrinsics"

// Only the checks the Kotlin compiler inserts at parameters, platform-type results, `!!` and lateinit reads.
// throwNpe/throwJavaNpe are not listed: current compilers no longer emit them, and a throw cannot be dropped
// without a replacement value.
private val NULL_CHECKS =
    setOf(
        "checkNotNull",
        "checkNotNullParameter",
        "checkNotNullExpressionValue",
        "throwUninitializedPropertyAccessException",
    )

/**
 * Kotlin null handling: flips `IFNULL`/`IFNONNULL` (`?.`, `?:`, `== null`) and removes the `Intrinsics` null checks
 * (`!!`, parameter and platform-type checks, `lateinit` reads), leaving their arguments popped.
 */
internal object NullSafetyOperator : Operator {
    override val id = OperatorId("null-safety")

    override fun sites(method: MethodNode): List<Site> =
        method.instructions.mapNotNull { insn ->
            when {
                insn.opcode == IFNULL -> Site(insn, "ifnull -> ifnonnull")
                insn.opcode == IFNONNULL -> Site(insn, "ifnonnull -> ifnull")
                insn is MethodInsnNode && insn.owner == INTRINSICS && insn.name in NULL_CHECKS ->
                    Site(insn, insn.describe("remove"))
                else -> null
            }
        }

    override fun apply(
        method: MethodNode,
        site: Site,
    ) {
        when (val insn = site.instruction) {
            is JumpInsnNode -> insn.opcode = if (insn.opcode == IFNULL) IFNONNULL else IFNULL
            else -> method.replaceCall(insn as MethodInsnNode, null)
        }
    }
}
