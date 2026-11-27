package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.asm.filters.isInlineFunctionOriginal
import org.objectweb.asm.Opcodes.ACC_ABSTRACT
import org.objectweb.asm.Opcodes.ACC_BRIDGE
import org.objectweb.asm.Opcodes.ACC_ENUM
import org.objectweb.asm.Opcodes.ACC_NATIVE
import org.objectweb.asm.Opcodes.ACC_SYNTHETIC
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodNode

private const val NO_BODY_OR_GENERATED = ACC_SYNTHETIC or ACC_BRIDGE or ACC_ABSTRACT or ACC_NATIVE

// A value class in the signature mangles componentN and copy to "<name>-<hash>" (URL-safe base64).
private val DATA_MEMBER =
    Regex(
        """equals\(Ljava/lang/Object;\)Z|hashCode\(\)I|toString\(\)Ljava/lang/String;""" +
            """|component\d+(-[\w-]+)?\(\).*|copy(-[\w-]+)?\(.*""",
    )

/**
 * False for compiler-generated methods, methods without code, initializers and user-defined inline functions.
 * An inline body is exercised only through copies in callers, which the noise filters leave alone, so mutants of
 * the original method would survive for tests that never reach it. Needs debug info (the marker is a local).
 */
internal fun isMutable(method: MethodNode): Boolean =
    (method.access and NO_BODY_OR_GENERATED) == 0 &&
        method.name != "<init>" &&
        method.name != "<clinit>" &&
        !isInlineFunctionOriginal(method)

/**
 * The [isMutable] methods of this class, minus the members kotlinc generates for a data class, data object or
 * enum class, and none of a SAM adapter. kotlinc (K2, and K1 with the JVM IR backend) writes no line number into
 * data members; a member the user declares has its own. Without debug info every method lacks lines, so no data
 * member is recognised. Enum members need no lines: kotlinc rejects a user declaration with their signatures.
 */
internal fun ClassNode.mutableMethods(): List<MethodNode> {
    if (isSamAdapter()) return emptyList()
    val kotlin = info().isKotlin
    val recognises = kotlin && methods.any { it.hasLines() }
    return methods.filter {
        isMutable(it) && !(recognises && it.isGeneratedDataMember()) && !(kotlin && isGeneratedEnumMember(it))
    }
}

// A function value converted to a SAM interface is wrapped in a synthetic Caller$sam$Iface$0 whose
// equals, hashCode and getFunctionDelegate are generated, and so is the call it forwards.
private fun ClassNode.isSamAdapter() =
    access and ACC_SYNTHETIC != 0 && "kotlin/jvm/internal/FunctionAdapter" in interfaces.orEmpty()

private fun MethodNode.isGeneratedDataMember() = !hasLines() && DATA_MEMBER.matches(name + desc)

private fun ClassNode.isGeneratedEnumMember(method: MethodNode) =
    access and ACC_ENUM != 0 &&
        method.name + method.desc in
        setOf("values()[L$name;", "valueOf(Ljava/lang/String;)L$name;", "getEntries()Lkotlin/enums/EnumEntries;")

private fun MethodNode.hasLines() = instructions.any { it is LineNumberNode }
