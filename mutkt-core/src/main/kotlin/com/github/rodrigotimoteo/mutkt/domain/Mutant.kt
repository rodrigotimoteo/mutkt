package com.github.rodrigotimoteo.mutkt.domain

/** One mutation of a method; carries no bytecode. [line] is null when the class has no debug info. */
public data class Mutant(
    val id: MutantId,
    val operator: OperatorId,
    val className: String,
    val methodName: String,
    val methodDescriptor: String,
    val line: Int?,
    val description: String,
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
