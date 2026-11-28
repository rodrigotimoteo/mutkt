package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.ClassInfo
import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Opcodes.ACONST_NULL
import org.objectweb.asm.Opcodes.ARETURN
import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.Opcodes.POP
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodNode

// Array and collection returns belong to the empty-returns operator (an empty value instead of null), so the
// two operators never emit the same mutant. Decided by return descriptor only.
private const val CONTINUATION = "Lkotlin/coroutines/Continuation;"

// kotlin/Unit has a single value, so returning null instead of it is noise rather than a useful mutant.
private val SKIPPED_RETURN_TYPES =
    setOf("Ljava/util/List;", "Ljava/util/Set;", "Ljava/util/Map;", "Ljava/util/Collection;", "Lkotlin/Unit;")

/** Replaces the returned object with null, except for array, java.util collection, Unit and suspend returns. */
internal object NullReturnsOperator : Operator {
    override val id = OperatorId("null-returns")

    override fun sites(
        method: MethodNode,
        cls: ClassInfo,
    ): List<Site> =
        if (isCandidate(method.desc)) {
            method.instructions
                .filter { it.opcode == ARETURN }
                // previous is the adjacent node: a label or frame there marks a merge point, where the value
                // may not be null, so the site stays. Only a literal null returned directly is equivalent.
                .filter { it.previous?.opcode != ACONST_NULL }
                // A lambda or suspend block typed Object returns Unit.INSTANCE; callers never read a Unit result.
                .filterNot { isUnitInstance(it.previous) }
                .map { Site(it, "areturn -> null") }
        } else {
            emptyList()
        }

    // The original ARETURN stays and returns the pushed null.
    override fun apply(
        method: MethodNode,
        site: Site,
    ) = method.instructions.insertBefore(
        site.instruction,
        InsnList().apply {
            add(InsnNode(POP))
            add(InsnNode(ACONST_NULL))
        },
    )

    private fun isUnitInstance(insn: AbstractInsnNode?) =
        insn is FieldInsnNode && insn.opcode == GETSTATIC && insn.owner == "kotlin/Unit" && insn.name == "INSTANCE"

    private fun isCandidate(methodDesc: String): Boolean {
        val returnType = Type.getReturnType(methodDesc).descriptor
        // A suspend function returns Object (the ARETURN may carry COROUTINE_SUSPENDED) and takes a Continuation last.
        val suspends = Type.getArgumentTypes(methodDesc).lastOrNull()?.descriptor == CONTINUATION
        return !suspends && !returnType.startsWith("[") && returnType !in SKIPPED_RETURN_TYPES
    }
}
