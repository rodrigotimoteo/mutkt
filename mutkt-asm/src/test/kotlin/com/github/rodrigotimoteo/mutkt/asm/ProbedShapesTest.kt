package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.testing.IsolatedClasses
import com.github.rodrigotimoteo.mutkt.testing.TEST_PROBE_REGISTRY
import com.github.rodrigotimoteo.mutkt.testing.clearProbes
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes.ACC_INTERFACE
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter

private const val SOURCE_KT = "SourceKt"

/** Every class kotlinc emits gets probes, verifies and behaves as before: one bad rewrite would cost its reach. */
class ProbedShapesTest {
    private val classes = compileKotlin(SOURCE)
    private val files = classes.map { (name, bytes) -> ClassFile(name, bytes) }
    private val mutants = AsmMutantGenerator(::error, ::systemClass).discover(files, files)
    private val instrumented = AsmCoverageInstrumenter(TEST_PROBE_REGISTRY, ::systemClass).instrument(files, mutants)
    private val probed = IsolatedClasses(classes + instrumented.classes.associate { it.name to it.bytes })
    private val loader = probed.type(SOURCE_KT).classLoader

    @BeforeEach
    fun forgetProbes() = clearProbes()

    @Test
    fun `every class but the interfaces is probed and verifies`() {
        val interfaces = files.filter { parse(it.bytes).access and ACC_INTERFACE != 0 }.map { it.name }.toSet()

        assertEquals(interfaces, instrumented.unprobed)
        assertEquals(classes.keys - interfaces, instrumented.classes.map { it.name }.toSet())
        for (probedClass in instrumented.classes) {
            val log = StringWriter()
            CheckClassAdapter.verify(ClassReader(probedClass.bytes), loader, false, PrintWriter(log))
            assertEquals("", log.toString(), probedClass.name)
        }
    }

    @Test
    fun `probed classes initialise and behave like the originals`() {
        classes.keys.forEach { Class.forName(it, true, loader) }

        assertEquals(IsolatedClasses(classes).invoke(SOURCE_KT, "exercise"), probed.invoke(SOURCE_KT, "exercise"))
    }

    @Test
    fun `running the code reaches every class it entered`() {
        probed.invoke(SOURCE_KT, "exercise")

        val reached = instrumented.classes.map { it.name }.filter { probed.probes(it, PROBE_FIELD)?.last() == true }
        val entered =
            "$SOURCE_KT Registry Color Clock Clock\$Companion Point Box Base Derived Outer Outer\$In Meters Thing"
                .split(" ")
        assertTrue(reached.containsAll(entered), "${entered - reached.toSet()} not reached")
    }

    private companion object {
        // Kotlin shapes with their own bytecode: object, enum, companion <clinit>, data class, $default, bridge,
        // super(args), inner and local classes, suspend state machine, value class, DefaultImpls, loops at entry.
        val SOURCE =
            """
            import kotlin.coroutines.Continuation
            import kotlin.coroutines.EmptyCoroutineContext
            import kotlin.coroutines.startCoroutine

            object Registry { val items = listOf(1, 2, 3); fun total() = items.sum() }
            enum class Color { RED, GREEN; fun next() = entries[(ordinal + 1) % entries.size] }
            class Clock { companion object { val start = System.nanoTime() > 0; fun ok() = start } }
            data class Point(val x: Int, val y: Int)
            fun greet(name: String = "you", times: Int = 1) = "hi ${'$'}name".repeat(times)
            class Box(val v: Int) : Comparable<Box> { override fun compareTo(other: Box) = v - other.v }
            open class Base(val n: Int)
            class Derived(n: Int, label: String) : Base(n + label.length)
            class Outer(val k: Int) { inner class In { fun f() = k * 2 } }
            fun local(): Int { class L { fun v() = 3 }; return L().v() }
            suspend fun twice(x: Int) = x * 2
            @JvmInline value class Meters(val v: Int) { operator fun plus(o: Meters) = Meters(v + o.v) }
            interface Named { fun name() = "named" }
            class Thing : Named
            fun spin(n: Int): Int { var i = n; while (i > 0) i--; return i }

            fun exercise(): String {
                var suspended = 0
                suspend { twice(21) }.startCoroutine(Continuation(EmptyCoroutineContext) { suspended = it.getOrThrow() })
                return listOf(
                    Registry.total(), Color.RED.next(), Clock.ok(), Point(1, 2).copy(y = 5), greet(), greet("me", 2),
                    listOf(Box(3), Box(1)).sorted().map { it.v }, Derived(1, "ab").n, Outer(4).In().f(), local(),
                    suspended, (Meters(2) + Meters(3)).v, Thing().name(), spin(3),
                ).joinToString()
            }
            """.trimIndent()

        // Kotlin stdlib types for frame computation come from the test JVM's own classpath.
        fun systemClass(internalName: String): ByteArray? =
            ClassLoader.getSystemResourceAsStream("$internalName.class")?.use { it.readBytes() }
    }
}
