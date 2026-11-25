package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.ICONST_1
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.util.Printer
import java.security.MessageDigest

/** Parity fixtures compiled the way tools/parity-legacy did: fixture name to class bytes by binary name. */
internal val parityClasses: Map<String, Map<String, ByteArray>> by lazy {
    val names = listOf("Arithmetic", "Classes", "Conditionals", "Empties", "Nulls", "Returns")
    names.associate { name ->
        val text = checkNotNull(LegacyTable::class.java.getResourceAsStream("/parity/Parity$name.kt.txt")) { name }
        "Parity$name" to compileKotlin(text.bufferedReader().use { it.readText() })
    }
}

internal fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Sites of [operator] in every parity class, as engine discovery reports them. */
internal fun newRows(operator: Operator): List<Row> =
    parityClasses.values.flatMap { it.values }.flatMap { bytes ->
        val node = parse(bytes)
        val sites = node.methods.filter(::isMutable).flatMap { sitesOf(operator, node.name, it) }
        val found = discover(bytes, operator)
        check(found.size == sites.size) { "discover and sites disagree for ${operator.id.value}" }
        found.zip(sites) { found, site ->
            Row(
                operator.id.value,
                found.className.replace('/', '.'),
                found.methodName + found.descriptor,
                found.line ?: -1,
                Printer.OPCODES[opcodeOf(operator, site.instruction)],
            )
        }
    }

// Legacy true/false-returns rows carry the ICONST_0/ICONST_1 pushed before the IRETURN, not the IRETURN. Rows
// are described the same way here, so a site can only pair with a legacy site that has the same constant.
private fun opcodeOf(
    operator: Operator,
    instruction: AbstractInsnNode,
): Int {
    val previous = generateSequence(instruction.previous) { it.previous }.firstOrNull { it.opcode >= 0 }
    return when {
        operator.id.value !in setOf("true-returns", "false-returns") -> instruction.opcode
        previous?.opcode == ICONST_0 || previous?.opcode == ICONST_1 -> previous.opcode
        else -> instruction.opcode
    }
}
