package com.github.rodrigotimoteo.mutation.mutator

import org.objectweb.asm.Opcodes

/**
 * Mutates data class copy() method parameters.
 *
 * When a data class has a copy() method, this mutator creates mutants that:
 * - Replace individual parameters with default values (0, null, empty string)
 * - Skip specific parameters (use original value)
 *
 * Example:
 * ```
 * data class User(val name: String, val age: Int)
 * val copy = user.copy(age = 31)
 * ```
 * Mutants:
 * - copy(name = "") → empty name
 * - copy(age = 0) → zero age
 */
object DataClassCopyMutator {
    /**
     * Checks if a class is a data class by examining its methods.
     * Data classes have copy(), componentN(), toString(), hashCode(), equals().
     */
    fun isDataClass(methods: List<MethodInfo>): Boolean {
        val methodNames = methods.map { it.name }.toSet()
        return methodNames.contains("copy") &&
            methodNames.any { it.startsWith("component") } &&
            methodNames.contains("toString") &&
            methodNames.contains("hashCode") &&
            methodNames.contains("equals")
    }

    /**
     * Finds copy() method and its parameters for mutation.
     */
    fun findCopyMethods(methods: List<MethodInfo>): List<MethodInfo> {
        return methods.filter { it.name == "copy" && !it.isSynthetic }
    }

    /**
     * Generates mutations for a copy() method call.
     * Each parameter can be mutated to a default value.
     */
    fun generateMutations(
        className: String,
        method: MethodInfo,
        lineNumber: Int,
    ): List<MutationInfo> {
        val mutations = mutableListOf<MutationInfo>()

        // Parse the copy method descriptor to get parameter types
        val paramTypes = org.objectweb.asm.Type.getArgumentTypes(method.descriptor)

        // For each parameter, create a mutation
        for (i in paramTypes.indices) {
            val paramType = paramTypes[i]

            // Create mutation info for replacing this parameter with default
            val defaultValue = getDefaultValue(paramType)
            mutations.add(
                MutationInfo(
                    operator = MutationOperator.DATA_CLASS_COPY,
                    className = className,
                    methodName = method.name,
                    methodDescriptor = method.descriptor,
                    lineNumber = lineNumber,
                    description = "Copy param $i: ${paramType.className} -> $defaultValue",
                    originalOpcode = Opcodes.INVOKEVIRTUAL,
                    mutatedOpcode = Opcodes.INVOKEVIRTUAL,
                    metadata =
                        mapOf(
                            "parameterIndex" to i.toString(),
                            "parameterType" to paramType.descriptor,
                            "defaultValue" to defaultValue,
                        ),
                ),
            )
        }

        return mutations
    }

    /**
     * Gets the default value for a type.
     */
    private fun getDefaultValue(type: org.objectweb.asm.Type): String {
        return when (type.sort) {
            org.objectweb.asm.Type.BOOLEAN -> "false"
            org.objectweb.asm.Type.BYTE -> "0"
            org.objectweb.asm.Type.SHORT -> "0"
            org.objectweb.asm.Type.INT -> "0"
            org.objectweb.asm.Type.LONG -> "0L"
            org.objectweb.asm.Type.FLOAT -> "0.0f"
            org.objectweb.asm.Type.DOUBLE -> "0.0"
            org.objectweb.asm.Type.ARRAY -> "[]"
            org.objectweb.asm.Type.OBJECT -> {
                if (type.className == "java.lang.String") {
                    "\"\""
                } else {
                    "null"
                }
            }
            else -> "null"
        }
    }
}

/**
 * Information about a method in a class.
 */
data class MethodInfo(
    val name: String,
    val descriptor: String,
    val access: Int,
    val signature: String? = null,
) {
    val isStatic: Boolean get() = (access and Opcodes.ACC_STATIC) != 0
    val isSynthetic: Boolean get() = (access and Opcodes.ACC_SYNTHETIC) != 0
    val isBridge: Boolean get() = (access and Opcodes.ACC_BRIDGE) != 0
}
