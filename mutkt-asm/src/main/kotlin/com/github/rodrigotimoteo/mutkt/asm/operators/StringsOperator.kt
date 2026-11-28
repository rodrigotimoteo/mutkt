package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.ClassInfo
import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Opcodes.ILOAD
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.Opcodes.INVOKEVIRTUAL
import org.objectweb.asm.Opcodes.ISTORE
import org.objectweb.asm.Opcodes.POP
import org.objectweb.asm.Opcodes.POP2
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode

private const val BUILDER = "java/lang/StringBuilder"
private const val VALUE_TAG = '\u0001'
private const val CONSTANT_TAG = '\u0002'
private const val MAX_QUOTE = 30
private const val PLUMBING = "kotlin/jvm/internal/"
private const val STRING = "Ljava/lang/String;"
private const val OBJECT = "Ljava/lang/Object;"
private val PARTS = Regex("\\u0001|[^\\u0001]+")

private val EQUALS = setOf("equals", "equalsIgnoreCase")
private val MAP_KEYS = setOf("get", "put", "containsKey", "getOrDefault")

private val BUNDLE_KEY: (String) -> Boolean = {
    it.startsWith("get") || it.startsWith("put") || it == "containsKey" || it == "remove"
}

// A literal in the key position of these calls (their first String or Object parameter) is a name, not text: a
// class, resource, member, property, environment variable, enum constant, map or Android preference/extra key, or
// the constant a `when` on a String compares with.
private val KEY_CALLS: Map<String, (String) -> Boolean> =
    mapOf(
        "java/lang/Class" to
            setOf(
                "forName",
                "getMethod",
                "getDeclaredMethod",
                "getField",
                "getDeclaredField",
                "getResource",
                "getResourceAsStream",
            )::contains,
        "java/lang/ClassLoader" to setOf("loadClass", "getResource", "getResources", "getResourceAsStream")::contains,
        "java/lang/System" to setOf("getProperty", "setProperty", "clearProperty", "getenv")::contains,
        "java/lang/Integer" to { it == "getInteger" },
        "java/lang/Long" to { it == "getLong" },
        "java/lang/Boolean" to { it == "getBoolean" },
        "java/lang/String" to EQUALS::contains,
        "java/lang/Enum" to { it == "valueOf" },
        "android/content/SharedPreferences" to { it.startsWith("get") || it == "contains" },
        "android/content/SharedPreferences\$Editor" to { it.startsWith("put") || it == "remove" },
        "android/os/BaseBundle" to BUNDLE_KEY,
        "android/os/Bundle" to BUNDLE_KEY,
        "android/content/Intent" to { it.endsWith("Extra") },
    )

// kotlinc's own names: parameter names given to null checks, the property of a lateinit check, `==` through areEqual.
// Intrinsics.stringPlus (older string concatenation) takes text, so its literal is a site.
private val PLUMBING_NAMES =
    setOf(
        "areEqual",
        "checkNotNull",
        "checkNotNullParameter",
        "checkNotNullExpressionValue",
        "checkParameterIsNotNull",
        "checkExpressionValueIsNotNull",
        "checkFieldIsNotNull",
        "checkReturnedValueIsNotNull",
        "throwUninitializedProperty",
        "throwUninitializedPropertyAccessException",
        "checkHasClass",
        "reifiedOperationMarker",
        "needClassReification",
        "throwUndefinedForReified",
    )

private class Edit(
    val site: Site,
    val rewrite: (MethodNode) -> Unit,
)

/** Empties a string literal, or drops one part (text or value) of a string concatenation or template. */
internal object StringsOperator : Operator {
    override val id = OperatorId("strings")

    override fun sites(
        method: MethodNode,
        cls: ClassInfo,
    ): List<Site> = edits(method).map { it.site }

    override fun apply(
        method: MethodNode,
        site: Site,
    ) = edits(method).single { it.site == site }.rewrite(method)
}

private fun edits(method: MethodNode): List<Edit> {
    val targets = jumpTargets(method)
    return method.instructions.flatMap { insn ->
        when {
            insn is LdcInsnNode -> listOfNotNull(literal(insn, targets))
            insn.isAppend() -> listOf(append(insn as MethodInsnNode))
            insn is InvokeDynamicInsnNode -> concatParts(insn)
            else -> emptyList()
        }
    }
}

// A literal that feeds a concatenation directly is one of its parts, dropped there; emptying it too would repeat
// that mutant.
private fun literal(
    ldc: LdcInsnNode,
    targets: Set<LabelNode>,
): Edit? {
    val text = ldc.cst as? String
    val consumer = consumerOf(ldc, targets)
    val call = consumer?.call
    val partOrKey = consumer.isKey() || call.isAppend() || (call as? InvokeDynamicInsnNode)?.recipe() != null
    return if (text.isNullOrEmpty() || partOrKey) null else Edit(Site(ldc, "${quoted(text)} -> \"\"")) { ldc.cst = "" }
}

// The builder stays on the stack and append returns it, so popping the arguments in place of the call keeps the
// stack as the next append expects it.
private fun append(call: MethodInsnNode): Edit {
    val args = Type.getArgumentTypes(call.desc)
    val description = "StringBuilder.append(${args.joinToString { it.className.substringAfterLast('.') }}) -> removed"
    return Edit(Site(call, description)) { method ->
        val pops = InsnList().apply { args.reversed().forEach { add(InsnNode(if (it.size == 2) POP2 else POP)) } }
        method.instructions.insertBefore(call, pops)
        method.instructions.remove(call)
    }
}

private fun AbstractInsnNode?.isAppend() =
    this is MethodInsnNode &&
        opcode == INVOKEVIRTUAL &&
        owner == BUILDER &&
        name == "append" &&
        Type.getReturnType(desc).internalName == BUILDER

private fun Consumer?.isKey(): Boolean {
    val call = this?.call as? MethodInsnNode ?: return false
    val keyAt = Type.getArgumentTypes(call.desc).indexOfFirst { it.descriptor == STRING || it.descriptor == OBJECT }
    val plumbing = call.owner.startsWith(PLUMBING) && call.name in PLUMBING_NAMES
    val comparedWith = argument == -1 && call.owner == "java/lang/String" && call.name in EQUALS
    return plumbing || comparedWith || (argument == keyAt && call.takesKey())
}

// Any java.util map (Map and its hash, linked, tree and concurrent implementations), a listed call, or an enum's
// static valueOf(String).
private fun MethodInsnNode.takesKey(): Boolean {
    val isMap = owner.startsWith("java/util/") && owner.endsWith("Map") && name in MAP_KEYS
    val enumConstant = opcode == INVOKESTATIC && name == "valueOf" && desc == "($STRING)L$owner;"
    return isMap || KEY_CALLS[owner]?.invoke(name) == true || (enumConstant && !owner.startsWith("java/lang/"))
}

// A recipe with constants passed as bootstrap arguments (tag 2, kotlinc's form for text holding a tag character)
// is not a site.
private fun InvokeDynamicInsnNode.recipe(): String? =
    (bsmArgs.firstOrNull() as? String)?.takeIf {
        bsm.owner == "java/lang/invoke/StringConcatFactory" &&
            bsm.name == "makeConcatWithConstants" &&
            CONSTANT_TAG !in it
    }

// One part per run of text and per value tag, in recipe order.
private fun concatParts(indy: InvokeDynamicInsnNode): List<Edit> {
    val recipe = indy.recipe() ?: return emptyList()
    val parts = PARTS.findAll(recipe).map { it.range }.toList()
    val types = Type.getArgumentTypes(indy.desc)
    return parts.mapIndexed { index, range ->
        val position = "concat part ${index + 1}/${parts.size}"
        if (recipe[range.first] == VALUE_TAG) {
            val value = parts.take(index).count { recipe[it.first] == VALUE_TAG }
            val type = types[value].className.substringAfterLast('.')
            Edit(Site(indy, "$position ($type) -> dropped")) { dropValue(it, indy, value, range.first) }
        } else {
            Edit(Site(indy, "$position ${quoted(recipe.substring(range))} -> dropped")) {
                indy.bsmArgs[0] = recipe.removeRange(range)
            }
        }
    }
}

// The dropped value lies under the ones after it: those go to fresh locals, the value is popped, and they come back.
private fun dropValue(
    method: MethodNode,
    indy: InvokeDynamicInsnNode,
    value: Int,
    tagAt: Int,
) {
    val types = Type.getArgumentTypes(indy.desc)
    val later = types.drop(value + 1)
    val slots = later.runningFold(method.maxLocals) { slot, type -> slot + type.size }
    val code =
        InsnList().apply {
            later.indices.reversed().forEach { add(VarInsnNode(later[it].getOpcode(ISTORE), slots[it])) }
            add(InsnNode(if (types[value].size == 2) POP2 else POP))
            later.indices.forEach { add(VarInsnNode(later[it].getOpcode(ILOAD), slots[it])) }
        }
    method.maxLocals = slots.last()
    method.instructions.insertBefore(indy, code)
    val kept = types.filterIndexed { index, _ -> index != value }
    indy.desc = kept.joinToString("", "(", ")") { it.descriptor } + Type.getReturnType(indy.desc).descriptor
    indy.bsmArgs[0] = (indy.bsmArgs[0] as String).removeRange(tagAt, tagAt + 1)
}

private fun quoted(text: String): String {
    val shown = text.take(MAX_QUOTE).replace("\n", "\\n")
    return "\"$shown${if (text.length > MAX_QUOTE) "..." else ""}\""
}
