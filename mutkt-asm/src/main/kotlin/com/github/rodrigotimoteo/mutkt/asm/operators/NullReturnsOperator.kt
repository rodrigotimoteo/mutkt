package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.ClassInfo
import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Opcodes.ACONST_NULL
import org.objectweb.asm.Opcodes.ARETURN
import org.objectweb.asm.Opcodes.POP
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodNode

// Array and collection returns belong to the empty-returns operator (an empty value instead of null), so the
// two operators never emit the same mutant. Decided by return descriptor only.
private const val CONTINUATION = "Lkotlin/coroutines/Continuation;"

// kotlin/Unit has a single value, so returning null instead of it is noise rather than a useful mutant.
private val SKIPPED_RETURN_TYPES =
    setOf("Ljava/util/List;", "Ljava/util/Set;", "Ljava/util/Map;", "Ljava/util/Collection;", "Lkotlin/Unit;")

/**
 * Replaces the returned object with null where Kotlin allows it: the return is nullable or of unknown nullability
 * (private, generic, platform or Java), and is not an array, java.util collection, Unit or suspend return.
 */
internal object NullReturnsOperator : Operator {
    override val id = OperatorId("null-returns")

    override fun sites(
        method: MethodNode,
        cls: ClassInfo,
    ): List<Site> =
        // A non-null Kotlin return cannot be null: the caller's first use fails with an NPE, a trivial kill.
        if (isCandidate(method.desc) && !cls.declaresNonNull(method)) {
            method.instructions
                .filter { it.opcode == ARETURN }
                // previous is the adjacent node: a label or frame there marks a merge point, where the value
                // may not be null, so the site stays. Only a literal null returned directly is equivalent.
                .filter { it.previous?.opcode != ACONST_NULL }
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

    private fun isCandidate(methodDesc: String): Boolean {
        val returnType = Type.getReturnType(methodDesc).descriptor
        // A suspend function returns Object (the ARETURN may carry COROUTINE_SUSPENDED) and takes a Continuation last.
        val suspends = Type.getArgumentTypes(methodDesc).lastOrNull()?.descriptor == CONTINUATION
        return !suspends && !returnType.startsWith("[") && returnType !in SKIPPED_RETURN_TYPES
    }
}
