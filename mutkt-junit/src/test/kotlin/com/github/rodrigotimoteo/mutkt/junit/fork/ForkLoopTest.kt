package com.github.rodrigotimoteo.mutkt.junit.fork

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.junit.CALC
import com.github.rodrigotimoteo.mutkt.junit.CALC_MUTANT
import com.github.rodrigotimoteo.mutkt.junit.IMPORTS
import com.github.rodrigotimoteo.mutkt.junit.JUnitTestExecutor
import com.github.rodrigotimoteo.mutkt.junit.executorFor
import com.github.rodrigotimoteo.mutkt.junit.mutantBytes
import com.github.rodrigotimoteo.mutkt.junit.mutantOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

@Timeout(30)
class ForkLoopTest {
    private val executors = mutableListOf<JUnitTestExecutor>()

    @AfterEach
    fun closeExecutors() = executors.forEach { it.close() }

    private val addsTest = "class CalcTest { @Test fun adds() { assertEquals(5, Calc().add(2, 3)) } }"

    private fun executor() = executorFor("$IMPORTS$CALC\n$addsTest").also { executors += it }

    private fun run(bytes: ByteArray) = ForkRequest.Run(mutantOf(), bytes, 10_000)

    private fun input(vararg requests: ForkRequest): DataInputStream {
        val buffer = ByteArrayOutputStream()
        val out = DataOutputStream(buffer)
        requests.forEach { writeRequest(out, it) }
        return DataInputStream(ByteArrayInputStream(buffer.toByteArray()))
    }

    private fun replies(vararg requests: ForkRequest): List<ForkReply> {
        val buffer = ByteArrayOutputStream()
        runForkLoop(input(*requests), DataOutputStream(buffer), executor())
        val stream = DataInputStream(ByteArrayInputStream(buffer.toByteArray()))
        return generateSequence { readReply(stream) }.toList()
    }

    @Test
    fun `announces readiness, then answers a killed mutant`() {
        assertEquals(
            listOf(ForkReply.Ready(), ForkReply.Result(Outcome.Killed("CalcTest.adds"))),
            replies(run(mutantBytes(CALC_MUTANT)), ForkRequest.Shutdown),
        )
    }

    @Test
    fun `answers a surviving mutant and keeps serving requests`() {
        assertEquals(
            listOf(
                ForkReply.Ready(),
                ForkReply.Result(Outcome.Survived),
                ForkReply.Result(Outcome.Killed("CalcTest.adds")),
            ),
            replies(run(mutantBytes(CALC)), run(mutantBytes(CALC_MUTANT)), ForkRequest.Shutdown),
        )
    }

    @Test
    fun `shutdown stops the loop without reading further requests`() {
        assertEquals(listOf(ForkReply.Ready()), replies(ForkRequest.Shutdown, run(mutantBytes(CALC))))
    }

    @Test
    fun `clean end of input ends the loop`() {
        assertEquals(listOf(ForkReply.Ready()), replies())
    }

    @Test
    fun `a truncated frame is a protocol error`() {
        val truncated = DataInputStream(ByteArrayInputStream(byteArrayOf(0, 0, 0, 9, 1)))
        assertThrows<ForkProtocolException> {
            runForkLoop(truncated, DataOutputStream(ByteArrayOutputStream()), executor())
        }
    }
}
