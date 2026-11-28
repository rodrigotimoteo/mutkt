package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Hierarchy
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.asm.parse
import com.github.rodrigotimoteo.mutkt.testing.buildClass
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes.ARETURN
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter

private val SOURCE =
    """
    class Box(val v: Int)
    data class Point(val x: Int, val y: Int)
    enum class Color { RED, GREEN }

    fun str(a: String): String = a + "x"
    fun boxed(a: Int): Any = a
    fun box(a: Int) = Box(a)
    fun color(a: Int) = if (a > 0) Color.GREEN else Color.RED
    fun point(a: Int) = Point(a, a).copy(y = 7)
    fun <T : Any> identNonNull(a: T): T = a
    fun lambda(): () -> String = { "x" }

    fun boxedInt(a: Int): Int? = if (a > 0) a else null
    fun maybe(a: String?): String? = a
    fun <T> ident(a: T): T = a
    fun maybeBox(a: Int): Box? = Box(a)
    fun maybeColor(a: Int): Color? = if (a > 0) Color.GREEN else Color.RED
    fun maybePoint(a: Int): Point? = Point(a, a).copy(y = 7)
    fun platform(a: Int) = java.lang.String.valueOf(a)
    private fun priv(a: Int): String = "p" + a
    fun callPriv(a: Int): String? = priv(a)
    fun over(a: String): String? = a
    fun over(a: Int): String? = "n" + a
    fun two(a: Int): String? {
        if (a < 0) return "neg"
        return "pos"
    }
    fun pickNull(a: Boolean): String? = if (a) null else "x"
    fun callHi(): String? = Greeter().hi()
    fun callOverInt(): String? = over(1)
    fun callOverString(): String? = over("s")

    class Greeter { fun hi(): String? = "hi" }

    fun nothing(): String? = null
    fun nothingBox(): Box? = null
    fun count(a: Int): Int = a
    fun flag(a: Int): Boolean = a > 0
    fun big(a: Long): Double = a * 2.0
    fun unit(a: Int) { println(a) }

    fun list(): List<Int> = listOf(1)
    fun mutableList(): MutableList<Int> = mutableListOf(1)
    fun set(): Set<Int> = setOf(1)
    fun map(): Map<Int, Int> = mapOf(1 to 1)
    fun collection(): Collection<Int> = listOf(1)
    fun ints(): IntArray = intArrayOf(1)
    fun strings(): Array<String> = arrayOf("a")
    fun matrix(): Array<IntArray> = arrayOf(intArrayOf(1))
    fun arrayList(): ArrayList<Int>? = arrayListOf(1)

    suspend fun suspending(a: Int): String? = a.toString()
    suspend fun suspendingNothing(): Box? = null

    fun maybeUnit(a: Boolean): Unit? = if (a) Unit else null
    class Person(val name: String?)
    class Named(val name: String)
    """.trimIndent()

class NullReturnsOperatorTest {
    private val classes = compileKotlin(SOURCE)
    private val file = classes.getValue("SourceKt")
    private val noLookup: Hierarchy = { null }

    private fun sites(method: String) = discover(file, NullReturnsOperator).filter { it.methodName == method }

    private fun mutant(
        method: String,
        descriptor: String? = null,
        ordinal: Int = 0,
    ): Map<String, ByteArray> {
        val candidates = sites(method)
        val found = candidates.single { it.ordinal == ordinal && (descriptor == null || it.descriptor == descriptor) }
        return classes + ("SourceKt" to mutate(file, NullReturnsOperator, found, noLookup))
    }

    private fun verify(bytes: ByteArray) =
        StringWriter().also { CheckClassAdapter.verify(ClassReader(bytes), false, PrintWriter(it)) }.toString()

    @ParameterizedTest
    @ValueSource(strings = ["boxedInt", "maybe", "ident", "maybeBox", "maybeColor", "maybePoint", "platform"])
    fun `nullable and unknown object returns become null`(method: String) {
        val arg: Any = if (method == "maybe") "a" else 1
        val site = sites(method).single()
        val changed = mutant(method)

        assertNotNull(loadAndInvoke(classes, "SourceKt", method, arg), "$method original")
        assertNull(loadAndInvoke(changed, "SourceKt", method, arg), "$method mutant")
        assertEquals("areturn -> null", site.description)
        assertEquals("", verify(changed.getValue("SourceKt")))
    }

    @ParameterizedTest
    @ValueSource(strings = ["str", "boxed", "box", "color", "point", "identNonNull", "lambda"])
    fun `a return Kotlin declares non-null has no site`(method: String) {
        assertEquals(emptyList<Any>(), sites(method))
    }

    @Test
    fun `a private function carries no nullability annotation, so its return stays a site`() {
        val found = sites("priv").single()
        val changed = classes + ("SourceKt" to mutate(file, NullReturnsOperator, found, noLookup))

        assertEquals("p1", loadAndInvoke(classes, "SourceKt", "callPriv", 1))
        assertNull(loadAndInvoke(changed, "SourceKt", "callPriv", 1))
    }

    @Test
    fun `a method without Kotlin annotations, as javac emits, is a site`() {
        val java =
            buildClass("JavaLike", "name", "()Ljava/lang/String;") {
                visitLdcInsn("j")
                visitInsn(ARETURN)
            }
        val found = discover(java, NullReturnsOperator).single()

        val changed = mutate(java, NullReturnsOperator, found, noLookup)

        assertEquals("j", loadAndInvoke(mapOf("JavaLike" to java), "JavaLike", "name"))
        assertNull(loadAndInvoke(mapOf("JavaLike" to changed), "JavaLike", "name"))
    }

    @Test
    fun `overloads are separate sites told apart by descriptor`() {
        assertEquals(
            setOf("(Ljava/lang/String;)Ljava/lang/String;", "(I)Ljava/lang/String;"),
            sites("over").map { it.descriptor }.toSet(),
        )
        assertEquals(listOf(0, 0), sites("over").map { it.ordinal })
        val changed = mutant("over", "(I)Ljava/lang/String;")

        assertEquals(
            listOf("n1", "s"),
            listOf("callOverInt", "callOverString").map { loadAndInvoke(classes, "SourceKt", it) },
        )
        assertEquals(
            listOf(null, "s"),
            listOf("callOverInt", "callOverString").map { loadAndInvoke(changed, "SourceKt", it) },
        )
    }

    @Test
    fun `each return of a method is its own ordinal`() {
        assertEquals(listOf(0, 1), sites("two").map { it.ordinal })
        assertEquals(listOf("neg", "pos"), listOf(-1, 1).map { loadAndInvoke(classes, "SourceKt", "two", it) })
        assertEquals(
            listOf(null, "pos"),
            listOf(-1, 1).map {
                loadAndInvoke(mutant("two", ordinal = 0), "SourceKt", "two", it)
            },
        )
        assertEquals(
            listOf("neg", null),
            listOf(-1, 1).map {
                loadAndInvoke(mutant("two", ordinal = 1), "SourceKt", "two", it)
            },
        )
    }

    @Test
    fun `an instance method mutant is seen as null by its caller`() {
        val greeter = classes.getValue("Greeter")
        val found = discover(greeter, NullReturnsOperator).single { it.methodName == "hi" }
        val changed = mutate(greeter, NullReturnsOperator, found, noLookup)

        assertEquals("hi", loadAndInvoke(classes, "SourceKt", "callHi"))
        assertNull(loadAndInvoke(classes + ("Greeter" to changed), "SourceKt", "callHi"))
        assertEquals("", verify(changed))
    }

    @Test
    fun `a branch that merges with a null arm is still a site`() {
        assertEquals(1, sites("pickNull").size)
        assertNull(loadAndInvoke(mutant("pickNull"), "SourceKt", "pickNull", false))
    }

    @ParameterizedTest
    @ValueSource(strings = ["nothing", "nothingBox"])
    fun `returning a literal null is already null and not a site`(method: String) {
        assertEquals(emptyList<Any>(), sites(method))
    }

    @ParameterizedTest
    @ValueSource(strings = ["count", "flag", "big", "unit"])
    fun `primitive and void returns have no site`(method: String) {
        assertEquals(emptyList<Any>(), sites(method))
    }

    @ParameterizedTest
    @ValueSource(strings = ["list", "mutableList", "set", "map", "collection", "ints", "strings", "matrix"])
    fun `array and java util collection returns are left to empty returns`(method: String) {
        assertEquals(emptyList<Any>(), sites(method))
    }

    @Test
    fun `concrete collection classes are not excluded`() {
        assertEquals(1, sites("arrayList").size)
    }

    @Test
    fun `suspend functions are skipped because their return may be the suspension marker`() {
        assertEquals(emptyList<Any>(), sites("suspending") + sites("suspendingNothing"))
        assertEquals(
            listOf("(ILkotlin/coroutines/Continuation;)Ljava/lang/Object;"),
            descriptors(file, "suspending"),
        )
    }

    @Test
    fun `a method returning kotlin Unit is skipped`() {
        assertEquals(listOf("(Z)Lkotlin/Unit;"), descriptors(file, "maybeUnit"))
        assertEquals(emptyList<Any>(), sites("maybeUnit"))
    }

    @Test
    fun `a nullable property getter is a site, a non-null one is not`() {
        val person = classes.getValue("Person")
        val found = discover(person, NullReturnsOperator).single()
        val changed = mutate(person, NullReturnsOperator, found, noLookup)

        assertEquals("getName", found.methodName)
        assertEquals("", verify(changed))
        assertEquals(emptyList<Any>(), discover(classes.getValue("Named"), NullReturnsOperator))
    }

    private fun descriptors(
        bytes: ByteArray,
        method: String,
    ) = parse(bytes).methods.filter { it.name == method }.map { it.desc }
}
