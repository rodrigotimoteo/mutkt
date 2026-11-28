package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import com.github.rodrigotimoteo.mutkt.testing.IsolatedClasses
import com.github.rodrigotimoteo.mutkt.testing.TEST_PROBE_REGISTRY
import com.github.rodrigotimoteo.mutkt.testing.buildClass
import com.github.rodrigotimoteo.mutkt.testing.clearProbes
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes.ACC_FINAL
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.Opcodes.ACC_PUBLIC
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
private const val PLAIN = "Plain"
private const val SHAPE = "Shape"
private const val SQUARE = "SourceKt\$square\$1"
private val ARITHMETIC = OperatorId("arithmetic")

// The low byte of the class file's major version, and that version for Java 7.
private const val MAJOR_VERSION = 7
private const val JAVA_7 = 51

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
            fun square(a: Int, b: Int): Int = object : Shape {}.area(a, b)
            fun noop() {}
            class Plain { fun noop() {} }
            """.trimIndent(),
            kotlincArguments = listOf("-Xjvm-default=all"),
        )
    private val files = classes.map { (name, bytes) -> ClassFile(name, bytes) }
    private val mutants = AsmMutantGenerator(::error) { null }.discover(files, files)
    private val instrumented = AsmCoverageInstrumenter(TEST_PROBE_REGISTRY) { null }.instrument(files, mutants)
    private val probedBytes = instrumented.classes.associate { it.name to it.bytes }
    private val loaded = IsolatedClasses(classes + probedBytes)

    private fun mutant(
        method: String,
        operator: OperatorId,
        ordinal: Int = 0,
    ) = mutants.single { it.methodName == method && it.operator == operator && it.id.ordinal == ordinal }

    private fun hit(mutant: Mutant): Boolean {
        val probe = instrumented.probes.getValue(mutant.id)
        return loaded.probes(probe.className, PROBE_FIELD)?.get(probe.index) == true
    }

    // The registry outlives each test's loader; a class probed by an earlier test must start with none hit.
    @BeforeEach
    fun forgetProbes() = clearProbes()

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

    // Every method sets the last slot on entry.
    private fun reached(className: String) = loaded.probes(className, PROBE_FIELD)?.last() == true

    @Test
    fun `instrumented classes verify and behave like the originals`() {
        assertEquals(setOf(SOURCE_KT, PLAIN, SHAPE, SQUARE), probedBytes.keys)
        probedBytes.values.forEach { assertEquals("", verifyLog(it)) }
        val original = IsolatedClasses(classes)
        listOf(1, -1).forEach { a ->
            assertEquals(original.invoke(SOURCE_KT, "pick", a, 2), loaded.invoke(SOURCE_KT, "pick", a, 2))
        }
        assertEquals(original.invoke(SOURCE_KT, "failing", 2, 3), loaded.invoke(SOURCE_KT, "failing", 2, 3))
        assertEquals(original.invoke(SOURCE_KT, "square", 2, 3), loaded.invoke(SOURCE_KT, "square", 2, 3))
    }

    @Test
    fun `the probe array is a private static synthetic transient field set on first use`() {
        val field = parse(probedBytes.getValue(SOURCE_KT)).fields.single { it.name == PROBE_FIELD }

        assertEquals("[Z", field.desc)
        assertEquals(ACC_PRIVATE or ACC_STATIC or ACC_SYNTHETIC or ACC_TRANSIENT, field.access)
        assertNull(loaded.probes(SOURCE_KT, PROBE_FIELD))
    }

    @Test
    fun `a copy of the class defined by a sandbox loader hits the same probe array`() {
        val sandbox = loaded.sandbox()

        loaded.invoke(SOURCE_KT, "pick", 1, 2)
        sandbox.invoke(SOURCE_KT, "pick", -1, 2)

        assertNotSame(loaded.type(SOURCE_KT), sandbox.type(SOURCE_KT))
        assertSame(loaded.probes(SOURCE_KT, PROBE_FIELD), sandbox.probes(SOURCE_KT, PROBE_FIELD))
        assertTrue(hit(mutant("pick", ARITHMETIC, 0)))
        assertTrue(hit(mutant("pick", ARITHMETIC, 1)))
    }

    @Test
    fun `every mutant gets a probe`() = assertEquals(mutants.map { it.id }.toSet(), instrumented.probes.keys)

    @Test
    fun `a method without mutation sites reaches its class`() {
        assertTrue(mutants.none { it.methodName == "noop" })

        loaded.invoke(SOURCE_KT, "noop")

        assertTrue(reached(SOURCE_KT))
        assertTrue(mutants.filter { it.id in instrumented.probes }.none(::hit))
    }

    @Test
    fun `a class without mutants is probed and reached through its constructor`() {
        assertTrue(mutants.none { it.className == PLAIN })
        assertFalse(reached(PLAIN))

        loaded.type(PLAIN).getDeclaredConstructor().newInstance()

        assertTrue(reached(PLAIN))
    }

    @Test
    fun `an interface default method sets its site and entry probes, kept in a public static final field`() {
        val plus = mutant("area", ARITHMETIC, 0)
        assertEquals(SHAPE, plus.className)

        loaded.invoke(SOURCE_KT, "square", 1, 2)

        assertTrue(hit(plus))
        assertFalse(hit(mutant("area", ARITHMETIC, 1)))
        assertTrue(reached(SHAPE))
        assertEquals(emptySet<String>(), instrumented.unprobed)
        val field = parse(probedBytes.getValue(SHAPE)).fields.single { it.name == PROBE_FIELD }
        assertEquals(ACC_PUBLIC or ACC_STATIC or ACC_FINAL or ACC_SYNTHETIC, field.access)
    }

    @Test
    fun `an annotation interface is unprobed even with code`() {
        val source = "annotation class Tag { companion object { val all = listOf(1) } }"
        val tag = ClassFile("Tag", compileKotlin(source).getValue("Tag"))
        assertTrue(parse(tag.bytes).methods.any { it.instructions.size() > 0 })

        val result = AsmCoverageInstrumenter(TEST_PROBE_REGISTRY) { null }.instrument(listOf(tag), emptyList())

        assertEquals(setOf("Tag"), result.unprobed)
    }

    @Test
    fun `an interface of a class file older than Java 8 is unprobed`() {
        // Its private probe accessor needs class file version 52.
        val bytes = classes.getValue(SHAPE).copyOf().also { it[MAJOR_VERSION] = JAVA_7.toByte() }

        val old = listOf(ClassFile(SHAPE, bytes))

        val result = AsmCoverageInstrumenter(TEST_PROBE_REGISTRY) { null }.instrument(old, emptyList())

        assertEquals(setOf(SHAPE), result.unprobed)
    }

    @Test
    fun `a class whose frames need an unknown type is listed as unprobed`() {
        // The branches join B with String, so computing the frame needs B's supertypes, which no lookup knows.
        val source =
            "open class A\nclass B : A()\nclass Pick { fun p(f: Boolean, a: B, b: String): Any = if (f) a else b }"
        val pick = ClassFile("Pick", compileKotlin(source).getValue("Pick"))

        val result = AsmCoverageInstrumenter(TEST_PROBE_REGISTRY) { null }.instrument(listOf(pick), emptyList())

        assertEquals(setOf("Pick"), result.unprobed)
    }

    @Test
    fun `a class without mutants that fails to rewrite keeps its original bytes and is unprobed`() {
        // A class that already has the probe accessor cannot get probes.
        val odd = ClassFile("Odd", compileKotlin("class Odd { fun `\$mutktInit`() = 1 }").getValue("Odd"))

        val result = AsmCoverageInstrumenter(TEST_PROBE_REGISTRY) { null }.instrument(files + odd, mutants)

        assertEquals(setOf("Odd"), result.unprobed)
        assertEquals(instrumented.probes, result.probes)
    }

    @Test
    fun `a probe does not wait for a thread holding the class monitor`() {
        // Initialise the class first: initialisation may take the monitor, a probe must not.
        assertNull(loaded.probes(SOURCE_KT, PROBE_FIELD))
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
    fun `a probed method fails when the registry is not on the system class path`() {
        val missing = AsmCoverageInstrumenter("com.example.MissingRegistry") { null }.instrument(files, mutants)
        val probed = IsolatedClasses(classes + missing.classes.associate { it.name to it.bytes })

        assertThrows<ClassNotFoundException> { probed.invoke(SOURCE_KT, "pick", 1, 2) }
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

        val result = AsmCoverageInstrumenter(TEST_PROBE_REGISTRY) { null }.instrument(all, mutants + hugeMutants)

        assertTrue(hugeMutants.isNotEmpty())
        assertEquals(setOf(SOURCE_KT, PLAIN, SHAPE, SQUARE), result.classes.map { it.name }.toSet())
        assertEquals(instrumented.probes, result.probes)
        assertEquals(setOf("Huge"), result.unprobed)
    }
}
