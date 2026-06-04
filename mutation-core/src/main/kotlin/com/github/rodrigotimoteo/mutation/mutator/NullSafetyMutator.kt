package com.github.rodrigotimoteo.mutation.mutator

import org.objectweb.asm.Opcodes

/**
 * Mutates null-safety operators in Kotlin.
 *
 * This mutator handles:
 * - ?.let { } → .let { } (remove null check)
 * - ?: defaultValue → ?: "" or ?: null
 * - !! → . (force unwrap)
 * - Safe calls → direct calls
 *
 * Example:
 * ```
 * val result = input?.trim() ?: "default"
 * ```
 * Mutants:
 * - input?.trim() → input.trim() (remove null check)
 * - input?.trim() → null (always null)
 * - ?: "default" → ?: "" (empty default)
 */
object NullSafetyMutator {

    /**
     * Detects safe call operator (?.method()).
     * In bytecode, this appears as:
     * 1. ALOAD
     * 2. IFNULL (to end)
     * 3. INVOKEVIRTUAL
     */
    fun isSafeCall(instructions: List<InstructionInfo>): Boolean {
        // Look for pattern: IFNULL followed by INVOKEVIRTUAL
        for (i in instructions.indices) {
            if (instructions[i].opcode == Opcodes.IFNULL) {
                // Check if next meaningful instruction is INVOKEVIRTUAL
                for (j in i + 1 until minOf(i + 5, instructions.size)) {
                    if (instructions[j].opcode == Opcodes.INVOKEVIRTUAL) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Detects not-null assertion operator (!!).
     * In bytecode, this appears as:
     * 1. ALOAD
     * 2. DUP
     * 3. IFNULL (throw NPE)
     * 4. INVOKEVIRTUAL
     */
    fun isNotNullAssertion(instructions: List<InstructionInfo>): Boolean {
        // Look for pattern: DUP, IFNULL, ATHROW
        for (i in instructions.indices) {
            if (instructions[i].opcode == Opcodes.DUP) {
                for (j in i + 1 until minOf(i + 3, instructions.size)) {
                    if (instructions[j].opcode == Opcodes.IFNULL) {
                        // Check if there's an ATHROW after
                        for (k in j + 1 until minOf(j + 3, instructions.size)) {
                            if (instructions[k].opcode == Opcodes.ATHROW) {
                                return true
                            }
                        }
                    }
                }
            }
        }
        return false
    }

    /**
     * Detects elvis operator (?:).
     * In bytecode, this appears as:
     * 1. ALOAD
     * 2. DUP
     * 3. IFNONNULL (skip default)
     * 4. POP
     * 5. [default value]
     */
    fun isElvisOperator(instructions: List<InstructionInfo>): Boolean {
        // Look for pattern: DUP, IFNONNULL, POP
        for (i in instructions.indices) {
            if (instructions[i].opcode == Opcodes.DUP) {
                for (j in i + 1 until minOf(i + 3, instructions.size)) {
                    if (instructions[j].opcode == Opcodes.IFNONNULL) {
                        for (k in j + 1 until minOf(j + 3, instructions.size)) {
                            if (instructions[k].opcode == Opcodes.POP) {
                                return true
                            }
                        }
                    }
                }
            }
        }
        return false
    }

    /**
     * Generates mutations for null-safety operators.
     */
    fun generateMutations(
        className: String,
        methodName: String,
        methodDescriptor: String,
        instruction: InstructionInfo,
        type: NullSafetyType
    ): List<MutationInfo> {
        val mutations = mutableListOf<MutationInfo>()

        when (type) {
            NullSafetyType.SAFE_CALL -> {
                // Mutant 1: Remove null check (direct call)
                mutations.add(MutationInfo(
                    operator = MutationOperator.NULL_SAFETY,
                    className = className,
                    methodName = methodName,
                    methodDescriptor = methodDescriptor,
                    lineNumber = instruction.lineNumber,
                    description = "Remove null check (safe call -> direct call)",
                    originalOpcode = instruction.opcode,
                    mutatedOpcode = instruction.opcode,
                    metadata = mapOf("mutationType" to "REMOVE_NULL_CHECK")
                ))

                // Mutant 2: Always return null
                mutations.add(MutationInfo(
                    operator = MutationOperator.NULL_SAFETY,
                    className = className,
                    methodName = methodName,
                    methodDescriptor = methodDescriptor,
                    lineNumber = instruction.lineNumber,
                    description = "Always return null (safe call -> null)",
                    originalOpcode = instruction.opcode,
                    mutatedOpcode = instruction.opcode,
                    metadata = mapOf("mutationType" to "ALWAYS_NULL")
                ))
            }

            NullSafetyType.NOT_NULL_ASSERTION -> {
                // Mutant: Remove assertion (allow null)
                mutations.add(MutationInfo(
                    operator = MutationOperator.NULL_SAFETY,
                    className = className,
                    methodName = methodName,
                    methodDescriptor = methodDescriptor,
                    lineNumber = instruction.lineNumber,
                    description = "Remove not-null assertion (!! -> .)",
                    originalOpcode = instruction.opcode,
                    mutatedOpcode = instruction.opcode,
                    metadata = mapOf("mutationType" to "REMOVE_ASSERTION")
                ))
            }

            NullSafetyType.ELVIS -> {
                // Mutant 1: Use empty default
                mutations.add(MutationInfo(
                    operator = MutationOperator.NULL_SAFETY,
                    className = className,
                    methodName = methodName,
                    methodDescriptor = methodDescriptor,
                    lineNumber = instruction.lineNumber,
                    description = "Use empty default (elvis -> empty)",
                    originalOpcode = instruction.opcode,
                    mutatedOpcode = instruction.opcode,
                    metadata = mapOf("mutationType" to "EMPTY_DEFAULT")
                ))

                // Mutant 2: Always use default
                mutations.add(MutationInfo(
                    operator = MutationOperator.NULL_SAFETY,
                    className = className,
                    methodName = methodName,
                    methodDescriptor = methodDescriptor,
                    lineNumber = instruction.lineNumber,
                    description = "Always use default (elvis -> default)",
                    originalOpcode = instruction.opcode,
                    mutatedOpcode = instruction.opcode,
                    metadata = mapOf("mutationType" to "ALWAYS_DEFAULT")
                ))
            }
        }

        return mutations
    }
}

/**
 * Types of null-safety operators.
 */
enum class NullSafetyType {
    SAFE_CALL,        // ?.
    NOT_NULL_ASSERTION, // !!
    ELVIS             // ?:
}