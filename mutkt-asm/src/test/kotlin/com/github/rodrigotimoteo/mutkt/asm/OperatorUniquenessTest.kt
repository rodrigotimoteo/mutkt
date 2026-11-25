package com.github.rodrigotimoteo.mutkt.asm

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import java.nio.ByteBuffer

// Each pair mutates the same instruction to different opcodes (negation vs boundary, constant 1 vs 0),
// so the mutants differ.
private val SHARING_PAIRS =
    setOf(
        setOf("negate-conditionals", "conditionals-boundary"),
        setOf("true-returns", "false-returns"),
    )

internal class OperatorUniquenessTest {
    private val hierarchy: Hierarchy = { type ->
        corpus[type.replace('/', '.')]
            ?: javaClass.classLoader.getResourceAsStream("$type.class")?.use { it.readBytes() }
    }

    private fun identicalMutants(operators: List<Operator>): List<String> {
        val seen = mutableMapOf<ByteBuffer, Pair<Operator, String>>()
        return operators.flatMap { op ->
            corpus.values.flatMap { bytes ->
                discover(bytes, op).mapNotNull { site ->
                    val mutant = ByteBuffer.wrap(mutate(bytes, op, site, hierarchy))
                    val other = seen.putIfAbsent(mutant, op to site.toString())
                    other?.takeIf { it.first != op }?.let { "${it.first.id} at ${it.second} and ${op.id} at $site" }
                }
            }
        }
    }

    // A site is the instruction an operator will change; two operators claiming one instruction can both mutate it.
    // Only a switch may be claimed more than once by one operator (it has a site per branch).
    private fun sharedInstructions(operators: List<Operator>): List<String> =
        corpus.values.flatMap { bytes ->
            val node = parse(bytes)
            val cls = node.info()
            node.methods.filter(::isMutable).flatMap { method ->
                val claims =
                    operators.flatMap { op ->
                        op
                            .sites(method, cls)
                            .map { Triple(method.instructions.indexOf(it.instruction), op, it.instruction) }
                    }
                claims
                    .groupBy({ it.first }, { it.second to it.third })
                    .filterValues { claimed ->
                        val ids = claimed.map { it.first.id.value }.toSet()
                        val isSwitch =
                            claimed.first().second.let {
                                it is TableSwitchInsnNode ||
                                    it is LookupSwitchInsnNode
                            }
                        if (ids.size == 1) claimed.size > 1 && !isSwitch else ids !in SHARING_PAIRS
                    }.mapValues { (_, claimed) -> claimed.map { it.first } }
                    .map { (index, ops) ->
                        "${ops.joinToString {
                            it.id.toString()
                        }} both claim instruction $index of ${node.name}.${method.name}${method.desc}"
                    }
            }
        }

    @Test
    fun `no two operators emit the same mutant class`() {
        assertTrue(identicalMutants(Operators.all).isEmpty(), identicalMutants(Operators.all).joinToString("\n"))
    }

    @Test
    fun `no instruction is a site of two operators`() {
        assertTrue(sharedInstructions(Operators.all).isEmpty(), sharedInstructions(Operators.all).joinToString("\n"))
    }
}
