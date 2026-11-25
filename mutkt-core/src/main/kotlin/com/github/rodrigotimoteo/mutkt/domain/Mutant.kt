package com.github.rodrigotimoteo.mutkt.domain

/**
 * One mutation of a method; carries no bytecode. [line] is null when the class has no debug info; [sourceFile] is
 * the class's `SourceFile` name (no directory), null when absent.
 */
public data class Mutant(
    val id: MutantId,
    val operator: OperatorId,
    val className: String,
    val methodName: String,
    val methodDescriptor: String,
    val line: Int?,
    val description: String,
    val sourceFile: String? = null,
) {
    init {
        require(
            id.operator == operator &&
                id.className == className &&
                id.methodName == methodName &&
                id.methodDescriptor == methodDescriptor,
        ) {
            "Mutant fields must match its id $id"
        }
    }
}
