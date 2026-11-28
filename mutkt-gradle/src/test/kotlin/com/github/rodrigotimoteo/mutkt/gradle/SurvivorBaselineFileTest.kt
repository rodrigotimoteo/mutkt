package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.application.Survivor
import com.github.rodrigotimoteo.mutkt.application.SurvivorBaseline
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantId
import com.github.rodrigotimoteo.mutkt.domain.MutantResult
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import com.github.rodrigotimoteo.mutkt.domain.MutationReport
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import com.github.rodrigotimoteo.mutkt.testing.buildClass
import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.Opcodes
import java.io.File

class SurvivorBaselineFileTest {
    @TempDir
    lateinit var dir: File

    private val file get() = dir.resolve("mutkt-baseline.json")
    private val survivors = listOf(Survivor("negate:a.Foo#run()I:1", "m1"), Survivor("negate:a.Foo#run()I:0", "m0"))

    @Test
    fun `a survivor whose method is not in the compiled classes fails with its id`() {
        val op = OperatorId("negate")
        val mutant = Mutant(MutantId(op, "a.Foo", "gone", "()I", 0), op, "a.Foo", "gone", "()I", 1, "negated")
        val report = MutationReport(listOf(MutantResult(mutant, MutantStatus.SURVIVED)))
        val bytes =
            buildClass("a/Foo", "run") {
                visitInsn(Opcodes.ICONST_0)
                visitInsn(Opcodes.IRETURN)
            }

        listOf(bytes, null).forEach { classBytes ->
            val error = assertThrows(GradleException::class.java) { survivorsOf(report) { classBytes } }

            assertEquals(
                "MutKt cannot key survivor ${mutant.id} for the survivor baseline: " +
                    "a.Foo#gone()I is not in the compiled classes",
                error.message,
            )
        }
    }

    @Test
    fun `a missing file accepts nothing`() = assertEquals(SurvivorBaseline(emptySet()), readSurvivorBaseline(file))

    @Test
    fun `writes a sorted, readable file that reads back`() {
        writeSurvivorBaseline(file, survivors)

        assertEquals(SurvivorBaseline(survivors.toSet()), readSurvivorBaseline(file))
        val text = file.readText()
        assertTrue(text.indexOf(":0\"") < text.indexOf(":1\"") && text.endsWith("}\n") && text.lines().size > 3, text)
    }

    @Test
    fun `a corrupt file fails with the path and how to recover`() {
        listOf("{not json", "", "{\"version\": 9, \"survivors\": []}").forEach { text ->
            file.writeText(text)

            val error = assertThrows(GradleException::class.java) { readSurvivorBaseline(file) }

            assertTrue(
                error.message!!.startsWith("The MutKt survivor baseline ${file.path} is corrupt ("),
                error.message,
            )
            assertTrue(error.message!!.endsWith("fix it or run ./gradlew mutktUpdateBaseline"), error.message)
        }
    }

    @Test
    fun `no failure when every survivor is accepted`() =
        assertNull(newSurvivorsFailure(SurvivorBaseline(survivors.toSet()), survivors, "b.json"))

    @Test
    fun `names new survivors and those whose method changed since accepted`() {
        val baseline = SurvivorBaseline(setOf(Survivor("negate:a.Foo#run()I:0", "old")))

        val message = newSurvivorsFailure(baseline, survivors, "b.json")

        assertEquals(
            """
            2 survivors are not in the MutKt survivor baseline b.json:
              negate:a.Foo#run()I:1
              negate:a.Foo#run()I:0 (accepted before its method changed: review it)
            Kill them, or accept them with ./gradlew mutktUpdateBaseline
            """.trimIndent(),
            message,
        )
    }

    @Test
    fun `lists at most ten new survivors`() {
        val many = (0 until 12).map { Survivor("negate:a.Foo#run()I:$it", "m") }

        val message = newSurvivorsFailure(SurvivorBaseline(emptySet()), many, "b.json")!!

        assertTrue(message.startsWith("12 survivors are not in"), message)
        assertTrue("run()I:9\n  ... and 2 more\n" in message && "run()I:10" !in message, message)
    }
}
