package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import com.github.rodrigotimoteo.mutkt.testing.IsolatedClasses
import com.github.rodrigotimoteo.mutkt.testing.buildClass
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ACC_SYNTHETIC
import org.objectweb.asm.Opcodes.ACC_TRANSIENT
import org.objectweb.asm.Opcodes.IADD
import org.objectweb.asm.Opcodes.ILOAD
import org.objectweb.asm.Opcodes.IRETURN
import org.objectweb.asm.Opcodes.NOP
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Duration
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

private const val SOURCE_KT = "SourceKt"
private val ARITHMETIC = OperatorId("arithmetic")

class AsmCoverageInstrumenterTest {
    // Interface bodies stay in the interface only with JVM default methods.
    private val classes =
        compileKotlin(
            """
            fun pick(a: Int, b: Int): Int = if (a > 0) a + b else a - b
            fun fin(a: Int, b: Int, fail: Boolean): Int {
                var r = 0
                try { if (fail) throw IllegalStateException() } finally { r = a + b }
                return r
            }
            fun failing(a: Int, b: Int): Int = try { fin(a, b, true) } catch (e: IllegalStateException) { -1 }
            interface Shape { fun area(a: Int, b: Int): Int = if (a > 0) a + b else a - b }
            """.trimIndent(),
            kotlincArguments = listOf("-Xjvm-default=all"),
        )
    private val files = classes.map { (name, bytes) -> ClassFile(name, bytes) }
    private val mutants = AsmMutantGenerator(::error) { null }.discover(files, files)
    private val instrumented = AsmCoverageInstrumenter { null }.instrument(files, mutants)
    private val probedBytes = instrumented.classes.associate { it.name to it.bytes }
    private val loaded = IsolatedClasses(classes + probedBytes)

    private fun mutant(
        method: String,
        operator: OperatorId,
        ordinal: Int = 0,
    ) = mutants.single { it.methodName == method && it.operator == operator && it.id.ordinal == ordinal }

    private fun hit(mutant: Mutant): Boolean {
        val probe = instrumented.probes.getValue(mutant.id)
        return loaded.probes(probe.className, instrumented.probeField)?.get(probe.index) == true
    }

    private fun verifyLog(bytes: ByteArray) =
        StringWriter().also { CheckClassAdapter.verify(ClassReader(bytes), false, PrintWriter(it)) }.toString()

    @Test
    fun `only the site execution reached is hit, even on the same line`() {
        val plus = mutant("pick", ARITHMETIC, 0)
        val minus = mutant("pick", ARITHMETIC, 1)
        assertEquals(plus.line, minus.line)

        loaded.invoke(SOURCE_KT, "pick", 1, 2)

        assertTrue(hit(plus))
        assertFalse(hit(minus))
        assertTrue(hit(mutant("pick", OperatorId("negate-conditionals"))))
    }

    @Test
    fun `a finally site is hit when only its exceptional copy runs`() {
        loaded.invoke(SOURCE_KT, "failing", 2, 3)

        assertTrue(hit(mutant("fin", ARITHMETIC)))
    }

    @Test
    fun `instrumented classes verify and behave like the originals`() {
        assertEquals(setOf(SOURCE_KT), probedBytes.keys)
        probedBytes.values.forEach { assertEquals("", verifyLog(it)) }
        val original = IsolatedClasses(classes)
        listOf(1, -1).forEach { a ->
            assertEquals(original.invoke(SOURCE_KT, "pick", a, 2), loaded.invoke(SOURCE_KT, "pick", a, 2))
        }
        assertEquals(original.invoke(SOURCE_KT, "failing", 2, 3), loaded.invoke(SOURCE_KT, "failing", 2, 3))
    }

    @Test
    fun `the probe array is a private static synthetic transient field set on first use`() {
        val field = parse(probedBytes.getValue(SOURCE_KT)).fields.single { it.name == instrumented.probeField }

        assertEquals("[Z", field.desc)
        assertEquals(ACC_PRIVATE or ACC_STATIC or ACC_SYNTHETIC or ACC_TRANSIENT, field.access)
        assertNull(loaded.probes(SOURCE_KT, instrumented.probeField))
    }

    @Test
    fun `every mutant outside interfaces gets a probe`() {
        val (inShape, others) = mutants.partition { it.className == "Shape" }

        assertTrue(inShape.isNotEmpty())
        assertEquals(others.map { it.id }.toSet(), instrumented.probes.keys)
    }

    @Test
    fun `a probe does not wait for a thread holding the class monitor`() {
        // Initialise the class first: initialisation may take the monitor, a probe must not.
        assertNull(loaded.probes(SOURCE_KT, instrumented.probeField))
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder =
            thread {
                synchronized(loaded.type(SOURCE_KT)) {
                    held.countDown()
                    release.await()
                }
            }
        held.await()

        try {
            assertEquals(
                3,
                assertTimeoutPreemptively<Any?>(Duration.ofSeconds(10)) { loaded.invoke(SOURCE_KT, "pick", 1, 2) },
            )
        } finally {
            release.countDown()
            holder.join()
        }
    }

    @Test
    fun `a class whose method would outgrow the size limit stays uninstrumented`() {
        val huge =
            ClassFile(
                "Huge",
                buildClass("Huge", "add", "(II)I") {
                    // 65528 + 4 bytes is just under the 65535-byte method limit; one probe pushes it over.
                    repeat(65_528) { visitInsn(NOP) }
                    visitVarInsn(ILOAD, 0)
                    visitVarInsn(ILOAD, 1)
                    visitInsn(IADD)
                    visitInsn(IRETURN)
                },
            )
        val all = files + huge
        val hugeMutants = AsmMutantGenerator(::error) { null }.discover(all, listOf(huge))

        val result = AsmCoverageInstrumenter { null }.instrument(all, mutants + hugeMutants)

        assertTrue(hugeMutants.isNotEmpty())
        assertEquals(setOf(SOURCE_KT), result.classes.map { it.name }.toSet())
        assertEquals(instrumented.probes, result.probes)
    }
}
