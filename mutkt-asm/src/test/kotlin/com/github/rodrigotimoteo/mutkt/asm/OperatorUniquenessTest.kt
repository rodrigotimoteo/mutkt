package com.github.rodrigotimoteo.mutkt.asm

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

// Both mutate the same conditional jump but to different opcodes (negation vs boundary), so the mutants differ.
private val SHARING_PAIR = setOf("negate-conditionals", "conditionals-boundary")

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
    private fun sharedInstructions(operators: List<Operator>): List<String> =
        corpus.values.flatMap { bytes ->
            val node = parse(bytes)
            node.methods.filter(::isMutable).flatMap { method ->
                val claims =
                    operators.flatMap { op ->
                        op.sites(method).map {
                            method.instructions.indexOf(it.instruction) to
                                op
                        }
                    }
                claims
                    .groupBy({ it.first }, { it.second })
                    .filterValues { it.size > 1 && it.map { op -> op.id.value }.toSet() != SHARING_PAIR }
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
