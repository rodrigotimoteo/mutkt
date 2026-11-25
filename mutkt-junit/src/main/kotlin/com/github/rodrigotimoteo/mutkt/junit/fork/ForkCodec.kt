package com.github.rodrigotimoteo.mutkt.junit.fork

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.nio.ByteBuffer

// Frame = int length + payload; payload starts with a one-byte tag. No Java serialization.
internal const val MAX_FRAME_BYTES: Int = 64 * 1024 * 1024

private const val REQUEST_RUN: Int = 1
private const val REQUEST_SHUTDOWN: Int = 2
private const val REQUEST_BASELINE: Int = 3
private const val REPLY_READY: Int = 1
private const val REPLY_RESULT: Int = 2
private const val REPLY_STARTED: Int = 3

internal fun writeRequest(
    out: DataOutputStream,
    request: ForkRequest,
) = writeFrame(out) {
    when (request) {
        is ForkRequest.Run -> {
            writeByte(REQUEST_RUN)
            writeMutant(request.mutant)
            writeByteArray(request.bytes)
            writeLong(request.timeoutMillis)
        }
        is ForkRequest.Baseline -> {
            writeByte(REQUEST_BASELINE)
            writeLong(request.timeoutMillis)
        }
        ForkRequest.Shutdown -> writeByte(REQUEST_SHUTDOWN)
    }
}

/** Returns null on a clean end of stream at a frame boundary. */
internal fun readRequest(input: DataInputStream): ForkRequest? =
    readFrame(input) {
        when (val tag = readUnsignedByte()) {
            REQUEST_RUN -> ForkRequest.Run(readMutant(), readByteArray(), readLong())
            REQUEST_BASELINE -> ForkRequest.Baseline(readLong())
            REQUEST_SHUTDOWN -> ForkRequest.Shutdown
            else -> throw ForkProtocolException("Unknown request tag $tag")
        }
    }

internal fun writeReply(
    out: DataOutputStream,
    reply: ForkReply,
) = writeFrame(out) {
    when (reply) {
        is ForkReply.Ready -> {
            writeByte(REPLY_READY)
            writeInt(reply.protocolVersion)
        }
        ForkReply.Started -> writeByte(REPLY_STARTED)
        is ForkReply.Result -> {
            writeByte(REPLY_RESULT)
            writeOutcome(reply.outcome)
            writeLong(reply.elapsedMillis)
        }
    }
}

/** Returns null on a clean end of stream at a frame boundary. */
internal fun readReply(input: DataInputStream): ForkReply? =
    readFrame(input) {
        when (val tag = readUnsignedByte()) {
            REPLY_READY -> ForkReply.Ready(readInt())
            REPLY_RESULT -> ForkReply.Result(readOutcome(), readLong())
            REPLY_STARTED -> ForkReply.Started
            else -> throw ForkProtocolException("Unknown reply tag $tag")
        }
    }

private fun writeFrame(
    out: DataOutputStream,
    body: DataOutputStream.() -> Unit,
) {
    val buffer = ByteArrayOutputStream()
    DataOutputStream(buffer).apply(body).flush()
    require(buffer.size() <= MAX_FRAME_BYTES) { "Frame of ${buffer.size()} bytes exceeds the $MAX_FRAME_BYTES limit" }
    out.writeInt(buffer.size())
    buffer.writeTo(out)
    out.flush()
}

private fun <T> readFrame(
    input: DataInputStream,
    body: DataInputStream.() -> T,
): T? {
    try {
        val stream = ByteArrayInputStream(readPayload(input) ?: return null)
        val result = DataInputStream(stream).body()
        require(stream.available() == 0) { "Frame has ${stream.available()} unexpected trailing bytes" }
        return result
    } catch (e: EOFException) {
        throw ForkProtocolException("Connection closed mid-frame", e)
    } catch (e: IllegalArgumentException) {
        throw ForkProtocolException("Invalid message: ${e.message}", e)
    }
}

private fun readPayload(input: DataInputStream): ByteArray? {
    val header = ByteArray(Int.SIZE_BYTES)
    val read = input.readNBytes(header, 0, header.size)
    if (read == 0) return null
    if (read < header.size) throw EOFException()
    val length = ByteBuffer.wrap(header).int
    if (length !in
        1..MAX_FRAME_BYTES
    ) {
        throw ForkProtocolException("Invalid frame length $length (max $MAX_FRAME_BYTES)")
    }
    return ByteArray(length).also(input::readFully)
}
