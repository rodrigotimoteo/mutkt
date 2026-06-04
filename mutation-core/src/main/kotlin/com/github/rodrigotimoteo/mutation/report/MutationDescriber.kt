package com.github.rodrigotimoteo.mutation.report

import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.objectweb.asm.Opcodes

/**
 * Provides human-readable descriptions for mutations.
 */
object MutationDescriber {
    /**
     * Get a readable description for a mutation.
     */
    fun describe(mutation: MutationInfo): String {
        return when (mutation.operator) {
            MutationOperator.CONDITIONALS_BOUNDARY -> describeConditionalBoundary(mutation)
            MutationOperator.NEGATE_CONDITIONALS -> describeNegateConditionals(mutation)
            MutationOperator.ARITHMETIC -> describeArithmetic(mutation)
            MutationOperator.RETURN_VALS -> describeReturnValue(mutation)
            MutationOperator.NULL_RETURNS -> "Return null"
            MutationOperator.EMPTY_RETURNS -> "Return empty collection/array"
            MutationOperator.INVERT_NEGS -> "Invert negation"
            MutationOperator.INCREMENTS -> describeIncrement(mutation)
            MutationOperator.TRUE_RETURNS -> "Boolean return: true -> false"
            MutationOperator.FALSE_RETURNS -> "Boolean return: false -> true"
            MutationOperator.VOID_METHOD_CALLS -> describeVoidMethodCall(mutation)
            MutationOperator.CONSTRUCTOR_CALLS -> describeConstructorCall(mutation)
            MutationOperator.NON_VOID_METHOD_CALLS -> describeNonVoidMethodCall(mutation)
            MutationOperator.DATA_CLASS_COPY -> "Data class copy mutation"
            MutationOperator.SEALED_WHEN -> "Sealed class when mutation"
            MutationOperator.NULL_SAFETY -> "Null-safety mutation"
            MutationOperator.COROUTINE -> "Coroutine mutation"
            else -> mutation.description
        }
    }

    private fun describeConditionalBoundary(mutation: MutationInfo): String {
        val original = opcodeToSymbol(mutation.originalOpcode)
        val mutated = opcodeToSymbol(mutation.mutatedOpcode)
        return "Boundary: $original -> $mutated"
    }

    private fun describeNegateConditionals(mutation: MutationInfo): String {
        val original = opcodeToSymbol(mutation.originalOpcode)
        val mutated = opcodeToSymbol(mutation.mutatedOpcode)
        return "Negate: $original -> $mutated"
    }

    private fun describeArithmetic(mutation: MutationInfo): String {
        val original = arithmeticOpcodeToSymbol(mutation.originalOpcode)
        val mutated = arithmeticOpcodeToSymbol(mutation.mutatedOpcode)
        return "Arithmetic: $original -> $mutated"
    }

    private fun describeReturnValue(mutation: MutationInfo): String {
        return when (mutation.mutatedOpcode) {
            Opcodes.ICONST_0 -> "Return 0"
            Opcodes.ICONST_1 -> "Return 1"
            Opcodes.ICONST_M1 -> "Return -1"
            Opcodes.ICONST_2 -> "Return 2"
            Opcodes.ICONST_3 -> "Return 3"
            Opcodes.ICONST_4 -> "Return 4"
            Opcodes.ICONST_5 -> "Return 5"
            Opcodes.LCONST_0 -> "Return 0L"
            Opcodes.LCONST_1 -> "Return 1L"
            Opcodes.FCONST_0 -> "Return 0.0f"
            Opcodes.FCONST_1 -> "Return 1.0f"
            Opcodes.FCONST_2 -> "Return 2.0f"
            Opcodes.DCONST_0 -> "Return 0.0"
            Opcodes.DCONST_1 -> "Return 1.0"
            else -> "Return constant"
        }
    }

    private fun describeIncrement(mutation: MutationInfo): String {
        return "Increment: ${mutation.originalOpcode} -> ${mutation.mutatedOpcode}"
    }

    private fun describeVoidMethodCall(mutation: MutationInfo): String {
        return "Remove void call: ${mutation.description.substringAfterLast(": ")}"
    }

    private fun describeConstructorCall(mutation: MutationInfo): String {
        return "Remove constructor: ${mutation.description.substringAfterLast(": ")}"
    }

    private fun describeNonVoidMethodCall(mutation: MutationInfo): String {
        return "Remove non-void call: ${mutation.description.substringAfterLast(": ")}"
    }

    private fun opcodeToSymbol(opcode: Int): String {
        return when (opcode) {
            Opcodes.IFEQ -> "== 0"
            Opcodes.IFNE -> "!= 0"
            Opcodes.IFLT -> "< 0"
            Opcodes.IFGE -> ">= 0"
            Opcodes.IFGT -> "> 0"
            Opcodes.IFLE -> "<= 0"
            Opcodes.IF_ICMPEQ -> "=="
            Opcodes.IF_ICMPNE -> "!="
            Opcodes.IF_ICMPLT -> "<"
            Opcodes.IF_ICMPGE -> ">="
            Opcodes.IF_ICMPGT -> ">"
            Opcodes.IF_ICMPLE -> "<="
            Opcodes.IF_ACMPEQ -> "=="
            Opcodes.IF_ACMPNE -> "!="
            else -> "?$opcode"
        }
    }

    private fun arithmeticOpcodeToSymbol(opcode: Int): String {
        return when (opcode) {
            Opcodes.IADD -> "+"
            Opcodes.ISUB -> "-"
            Opcodes.IMUL -> "*"
            Opcodes.IDIV -> "/"
            Opcodes.IREM -> "%"
            Opcodes.FADD -> "+"
            Opcodes.FSUB -> "-"
            Opcodes.FMUL -> "*"
            Opcodes.FDIV -> "/"
            Opcodes.FREM -> "%"
            Opcodes.DADD -> "+"
            Opcodes.DSUB -> "-"
            Opcodes.DMUL -> "*"
            Opcodes.DDIV -> "/"
            Opcodes.DREM -> "%"
            Opcodes.LADD -> "+"
            Opcodes.LSUB -> "-"
            Opcodes.LMUL -> "*"
            Opcodes.LDIV -> "/"
            Opcodes.LREM -> "%"
            else -> "?$opcode"
        }
    }
}
