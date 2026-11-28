package com.github.rodrigotimoteo.mutkt.junit.fork

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.domain.KillKind
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantId
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import java.io.DataInputStream
import java.io.DataOutputStream

private const val OUTCOME_KILLED: Int = 1
private const val OUTCOME_SURVIVED: Int = 2
private const val OUTCOME_NO_TESTS: Int = 3
private const val OUTCOME_LOAD_ERROR: Int = 4
private const val OUTCOME_TIMEOUT: Int = 5

internal fun DataOutputStream.writeByteArray(bytes: ByteArray) {
    writeInt(bytes.size)
    write(bytes)
}

internal fun DataOutputStream.writeString(value: String) = writeByteArray(value.toByteArray(Charsets.UTF_8))

internal fun DataInputStream.readByteArray(): ByteArray {
    val length = readInt()
    if (length < 0 || length > available()) throw ForkProtocolException("Invalid length $length inside frame")
    return ByteArray(length).also(::readFully)
}

internal fun DataInputStream.readString(): String = String(readByteArray(), Charsets.UTF_8)

internal fun DataOutputStream.writeMutant(mutant: Mutant) {
    writeString(mutant.id.operator.value)
    writeString(mutant.id.className)
    writeString(mutant.id.methodName)
    writeString(mutant.id.methodDescriptor)
    writeInt(mutant.id.ordinal)
    writeBoolean(mutant.line != null)
    mutant.line?.let(::writeInt)
    writeString(mutant.description)
    writeBoolean(mutant.sourceFile != null)
    mutant.sourceFile?.let(::writeString)
}

internal fun DataInputStream.readMutant(): Mutant {
    val operator = OperatorId(readString())
    val className = readString()
    val methodName = readString()
    val descriptor = readString()
    val id = MutantId(operator, className, methodName, descriptor, readInt())
    val line = if (readBoolean()) readInt() else null
    val description = readString()
    return Mutant(
        id,
        operator,
        className,
        methodName,
        descriptor,
        line,
        description,
        if (readBoolean()) readString() else null,
    )
}

internal fun DataOutputStream.writeOutcome(outcome: Outcome) {
    when (outcome) {
        is Outcome.Killed -> {
            writeByte(OUTCOME_KILLED)
            writeString(outcome.by)
            writeBoolean(outcome.cause != null)
            outcome.cause?.let(::writeString)
            writeByte(outcome.kind.ordinal)
        }
        Outcome.Survived -> writeByte(OUTCOME_SURVIVED)
        Outcome.NoTests -> writeByte(OUTCOME_NO_TESTS)
        is Outcome.LoadError -> {
            writeByte(OUTCOME_LOAD_ERROR)
            writeString(outcome.message)
        }
        Outcome.Timeout -> writeByte(OUTCOME_TIMEOUT)
    }
}

internal fun DataInputStream.readOutcome(): Outcome =
    when (val tag = readUnsignedByte()) {
        OUTCOME_KILLED -> {
            val by = readString()
            val cause = if (readBoolean()) readString() else null
            Outcome.Killed(by, readKillKind(), cause)
        }
        OUTCOME_SURVIVED -> Outcome.Survived
        OUTCOME_NO_TESTS -> Outcome.NoTests
        OUTCOME_LOAD_ERROR -> Outcome.LoadError(readString())
        OUTCOME_TIMEOUT -> Outcome.Timeout
        else -> throw ForkProtocolException("Unknown outcome tag $tag")
    }

private fun DataInputStream.readKillKind(): KillKind =
    readUnsignedByte().let { KillKind.entries.getOrNull(it) ?: throw ForkProtocolException("Unknown kill kind $it") }
