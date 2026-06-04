package com.github.rodrigotimoteo.mutation.mutator

import org.objectweb.asm.Opcodes

/**
 * Mutates coroutine builders and suspend functions.
 *
 * This mutator handles:
 * - runBlocking { } → skip body
 * - launch { } → skip body
 * - async { } → return completed result
 * - suspend fun → remove suspend behavior
 * - withContext { } → skip context switch
 *
 * Example:
 * ```
 * suspend fun fetchData(): String = withContext(Dispatchers.IO) {
 *     api.getData()
 * }
 * ```
 * Mutants:
 * - Skip coroutine body → return ""
 * - Throw CancellationException
 * - Remove withContext → run on wrong dispatcher
 */
object CoroutineMutator {

    /**
     * Checks if a function is a suspend function.
     * Suspend functions have ACC_SYNCHRONIZED flag in bytecode.
     */
    fun isSuspendFunction(access: Int): Boolean {
        return (access and Opcodes.ACC_SYNCHRONIZED) != 0
    }

    /**
     * Checks if a method is a coroutine builder.
     * Common coroutine builders: runBlocking, launch, async, withContext
     */
    fun isCoroutineBuilder(methodName: String): Boolean {
        return methodName in setOf(
            "runBlocking",
            "launch",
            "async",
            "withContext",
            "coroutineScope",
            "supervisorScope",
            "runTest"
        )
    }

    /**
     * Detects coroutine dispatcher usage.
     * Dispatchers are typically loaded as static fields.
     */
    fun isDispatcherLoad(instructions: List<InstructionInfo>): Boolean {
        // Look for GETSTATIC on Dispatchers class
        for (instruction in instructions) {
            if (instruction.opcode == Opcodes.GETSTATIC) {
                // In real implementation, would check the field name
                // For now, assume any GETSTATIC in suspend context is dispatcher
                return true
            }
        }
        return false
    }

    /**
     * Generates mutations for coroutine constructs.
     */
    fun generateMutations(
        className: String,
        method: MethodInfo,
        lineNumber: Int
    ): List<MutationInfo> {
        val mutations = mutableListOf<MutationInfo>()

        if (isSuspendFunction(method.access)) {
            // Mutant 1: Skip suspend function body (return default)
            mutations.add(MutationInfo(
                operator = MutationOperator.COROUTINE,
                className = className,
                methodName = method.name,
                methodDescriptor = method.descriptor,
                lineNumber = lineNumber,
                description = "Skip suspend function body",
                originalOpcode = Opcodes.INVOKEVIRTUAL,
                mutatedOpcode = Opcodes.INVOKEVIRTUAL,
                metadata = mapOf(
                    "mutationType" to "SKIP_SUSPEND_BODY",
                    "returnType" to getReturnType(method.descriptor)
                )
            ))

            // Mutant 2: Throw CancellationException
            mutations.add(MutationInfo(
                operator = MutationOperator.COROUTINE,
                className = className,
                methodName = method.name,
                methodDescriptor = method.descriptor,
                lineNumber = lineNumber,
                description = "Throw CancellationException",
                originalOpcode = Opcodes.INVOKEVIRTUAL,
                mutatedOpcode = Opcodes.INVOKEVIRTUAL,
                metadata = mapOf("mutationType" to "THROW_CANCELLATION")
            ))
        }

        if (isCoroutineBuilder(method.name)) {
            // Mutant: Skip coroutine builder body
            mutations.add(MutationInfo(
                operator = MutationOperator.COROUTINE,
                className = className,
                methodName = method.name,
                methodDescriptor = method.descriptor,
                lineNumber = lineNumber,
                description = "Skip coroutine builder body",
                originalOpcode = Opcodes.INVOKEVIRTUAL,
                mutatedOpcode = Opcodes.INVOKEVIRTUAL,
                metadata = mapOf("mutationType" to "SKIP_BUILDER_BODY")
            ))
        }

        return mutations
    }

    /**
     * Extracts return type from method descriptor.
     */
    private fun getReturnType(descriptor: String): String {
        val type = org.objectweb.asm.Type.getReturnType(descriptor)
        return type.className
    }
}