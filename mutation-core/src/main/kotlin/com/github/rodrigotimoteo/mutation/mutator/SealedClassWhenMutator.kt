package com.github.rodrigotimoteo.mutation.mutator

import org.objectweb.asm.Opcodes

/**
 * Mutates sealed class when expressions.
 *
 * When a sealed class has a when expression, this mutator creates mutants that:
 * - Remove branches (return IllegalStateException)
 * - Swap branch bodies
 * - Return wrong values for each branch
 *
 * Example:
 * ```
 * sealed class Result {
 *     data class Success(val data: String) : Result()
 *     data class Error(val msg: String) : Result()
 * }
 *
 * fun handle(result: Result) = when (result) {
 *     is Result.Success -> result.data
 *     is Result.Error -> throw Exception(result.msg)
 * }
 * ```
 * Mutants:
 * - Remove Success branch → throw IllegalStateException
 * - Remove Error branch → throw IllegalStateException
 */
object SealedClassWhenMutator {
    /**
     * Checks if a class is sealed by examining its metadata.
     * Sealed classes have isSealed = true in Kotlin metadata.
     */
    fun isSealedClass(metadata: Map<String, String>): Boolean {
        return metadata["isSealed"] == "true"
    }

    /**
     * Finds when expressions in bytecode.
     * When expressions compile to tableswitch or lookupswitch instructions.
     */
    fun findWhenExpressions(instructions: List<InstructionInfo>): List<InstructionInfo> {
        return instructions.filter {
            it.opcode == Opcodes.TABLESWITCH || it.opcode == Opcodes.LOOKUPSWITCH
        }
    }

    /**
     * Generates mutations for a when expression.
     * Each branch can be mutated to throw IllegalStateException.
     */
    fun generateMutations(
        className: String,
        methodName: String,
        methodDescriptor: String,
        whenInstruction: InstructionInfo,
        branchCount: Int,
    ): List<MutationInfo> {
        val mutations = mutableListOf<MutationInfo>()

        // For each branch, create a mutation that removes it
        for (i in 0 until branchCount) {
            mutations.add(
                MutationInfo(
                    operator = MutationOperator.SEALED_WHEN,
                    className = className,
                    methodName = methodName,
                    methodDescriptor = methodDescriptor,
                    lineNumber = whenInstruction.lineNumber,
                    description = "Remove when branch $i of $branchCount",
                    originalOpcode = whenInstruction.opcode,
                    mutatedOpcode = whenInstruction.opcode,
                    metadata =
                        mapOf(
                            "branchIndex" to i.toString(),
                            "branchCount" to branchCount.toString(),
                            "mutationType" to "REMOVE_BRANCH",
                        ),
                ),
            )
        }

        // Also create a mutation that returns wrong value for each branch
        for (i in 0 until branchCount) {
            mutations.add(
                MutationInfo(
                    operator = MutationOperator.SEALED_WHEN,
                    className = className,
                    methodName = methodName,
                    methodDescriptor = methodDescriptor,
                    lineNumber = whenInstruction.lineNumber,
                    description = "Return wrong value for when branch $i",
                    originalOpcode = whenInstruction.opcode,
                    mutatedOpcode = whenInstruction.opcode,
                    metadata =
                        mapOf(
                            "branchIndex" to i.toString(),
                            "branchCount" to branchCount.toString(),
                            "mutationType" to "WRONG_RETURN",
                        ),
                ),
            )
        }

        return mutations
    }
}

/**
 * Information about an instruction in bytecode.
 */
data class InstructionInfo(
    val opcode: Int,
    val lineNumber: Int,
    val label: org.objectweb.asm.Label? = null,
    val owner: String? = null,
)
