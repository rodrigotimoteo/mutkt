package com.github.rodrigotimoteo.mutkt.junit.fork

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantId
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class ForkCodecTest {
    private val operator = OperatorId("math")

    private fun mutant(
        line: Int? = 7,
        description: String = "a + b to a - b",
        className: String = "a.Calc",
    ) = Mutant(MutantId(operator, className, "add", "(II)I", 3), operator, className, "add", "(II)I", line, description)

    private fun frame(block: DataOutputStream.() -> Unit): DataInputStream {
        val payload = ByteArrayOutputStream()
        DataOutputStream(payload).apply(block).flush()
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply {
            writeInt(payload.size())
            payload.writeTo(this)
        }
        return DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
    }

    private fun encode(write: (DataOutputStream) -> Unit): ByteArray =
        ByteArrayOutputStream().also { write(DataOutputStream(it)) }.toByteArray()

    private fun roundTrip(request: ForkRequest): ForkRequest? =
        readRequest(DataInputStream(ByteArrayInputStream(encode { writeRequest(it, request) })))

    private fun roundTrip(reply: ForkReply): ForkReply? =
        readReply(DataInputStream(ByteArrayInputStream(encode { writeReply(it, reply) })))

    @Test
    fun `run request round-trips with a line`() {
        val request = ForkRequest.Run(mutant(), byteArrayOf(1, 2, 3), 1500L)
        assertEquals(request, roundTrip(request))
    }

    @Test
    fun `run request round-trips with null line empty strings and unicode`() {
        val plain = ForkRequest.Run(mutant(line = null, description = ""), ByteArray(0), 1L)
        val unicode =
            ForkRequest.Run(
                mutant(description = "é中😀", className = "a.Çalc"),
                byteArrayOf(0),
                Long.MAX_VALUE,
            )
        assertEquals(plain, roundTrip(plain))
        assertEquals(unicode, roundTrip(unicode))
    }

    @Test
    fun `run request carries one mebibyte of class bytes`() {
        val bytes = ByteArray(1 shl 20) { it.toByte() }
        val decoded = roundTrip(ForkRequest.Run(mutant(), bytes, 1L)) as ForkRequest.Run
        assertArrayEquals(bytes, decoded.bytes)
    }

    @Test
    fun `baseline request round-trips`() {
        assertEquals(ForkRequest.Baseline(1500L), roundTrip(ForkRequest.Baseline(1500L)))
    }

    @Test
    fun `baseline request rejects a non-positive timeout`() {
        assertThrows<IllegalArgumentException> { ForkRequest.Baseline(0) }
    }

    @Test
    fun `shutdown request, ready and started replies round-trip`() {
        assertEquals(ForkRequest.Shutdown, roundTrip(ForkRequest.Shutdown))
        assertEquals(ForkReply.Ready(), roundTrip(ForkReply.Ready()))
        assertEquals(ForkReply.Started, roundTrip(ForkReply.Started))
    }

    @Test
    fun `every outcome round-trips`() {
        listOf(
            Outcome.Killed("a.CalcTest.adds"),
            Outcome.Killed(""),
            Outcome.Survived,
            Outcome.NoTests,
            Outcome.LoadError("bad é class"),
            Outcome.Timeout,
        ).forEach { assertEquals(ForkReply.Result(it), roundTrip(ForkReply.Result(it))) }
    }

    @Test
    fun `several frames can be read from one stream`() {
        val bytes =
            encode {
                writeRequest(it, ForkRequest.Shutdown)
                writeRequest(it, ForkRequest.Run(mutant(), byteArrayOf(9), 5L))
            }
        val input = DataInputStream(ByteArrayInputStream(bytes))
        assertEquals(ForkRequest.Shutdown, readRequest(input))
        assertEquals(ForkRequest.Run(mutant(), byteArrayOf(9), 5L), readRequest(input))
    }

    @Test
    fun `truncated frame is a protocol error`() {
        val bytes = encode { writeRequest(it, ForkRequest.Run(mutant(), byteArrayOf(1, 2), 1L)) }
        val input = DataInputStream(ByteArrayInputStream(bytes.copyOf(bytes.size - 1)))
        val error = assertThrows<ForkProtocolException> { readRequest(input) }
        assertTrue("mid-frame" in error.message.orEmpty())
    }

    @Test
    fun `clean end of stream at a frame boundary yields null`() {
        assertNull(readReply(DataInputStream(ByteArrayInputStream(ByteArray(0)))))
        assertNull(readRequest(DataInputStream(ByteArrayInputStream(ByteArray(0)))))
    }

    @Test
    fun `end of stream inside the length header is a torn frame`() {
        val error =
            assertThrows<ForkProtocolException> { readReply(DataInputStream(ByteArrayInputStream(byteArrayOf(0, 0)))) }
        assertTrue("mid-frame" in error.message.orEmpty())
    }

    @Test
    fun `ready with another protocol version is incompatible`() {
        ForkReply.Ready().requireCompatible()
        val reply = roundTrip(ForkReply.Ready(PROTOCOL_VERSION + 1)) as ForkReply.Ready
        val error = assertThrows<ForkProtocolException> { reply.requireCompatible() }
        assertTrue("protocol" in error.message.orEmpty())
    }

    @Test
    fun `non-positive timeout is rejected on read and construction`() {
        assertThrows<IllegalArgumentException> { ForkRequest.Run(mutant(), ByteArray(0), 0L) }
        val input =
            frame {
                writeByte(1)
                repeat(4) { writeInt(1) }
                repeat(4) { writeByte(0) }
                writeInt(0)
            }
        assertThrows<ForkProtocolException> { readRequest(input) }
    }

    @Test
    fun `oversized negative and zero lengths are rejected`() {
        listOf(MAX_FRAME_BYTES + 1, -1, 0).forEach { length ->
            val input = DataInputStream(ByteArrayInputStream(encode { it.writeInt(length) }))
            val error = assertThrows<ForkProtocolException> { readRequest(input) }
            assertTrue("Invalid frame length $length" in error.message.orEmpty())
        }
    }

    @Test
    fun `unknown tags are rejected`() {
        assertThrows<ForkProtocolException> { readRequest(frame { writeByte(99) }) }
        assertThrows<ForkProtocolException> { readReply(frame { writeByte(99) }) }
        assertThrows<ForkProtocolException> {
            readReply(
                frame {
                    writeByte(2)
                    writeByte(99)
                },
            )
        }
    }

    @Test
    fun `inner length beyond the frame is rejected`() {
        listOf(1000, -5).forEach { length ->
            val input =
                frame {
                    writeByte(2)
                    writeByte(1)
                    writeInt(length)
                }
            assertThrows<ForkProtocolException> { readReply(input) }
        }
    }

    @Test
    fun `trailing bytes are rejected`() {
        assertThrows<ForkProtocolException> {
            readRequest(
                frame {
                    writeByte(2)
                    writeByte(0)
                },
            )
        }
    }

    @Test
    fun `blank operator fails the domain check as a protocol error`() {
        val input =
            frame {
                writeByte(1)
                repeat(4) { writeInt(0) }
            }
        val error = assertThrows<ForkProtocolException> { readRequest(input) }
        assertTrue("Invalid message" in error.message.orEmpty())
    }
}
