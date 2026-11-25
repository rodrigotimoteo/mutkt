package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Hierarchy
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.testing.buildClass
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.Opcodes.IRETURN
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter

private val SOURCE =
    """
    class Holder {
        lateinit var name: String
        fun nameOrFail(): String = name
    }
    class Own {
        private lateinit var secret: String
        fun read(): String = secret
        fun length(): Int = secret.length
        fun readInit(): String { secret = "s"; return secret }
    }
    private lateinit var hidden: String
    lateinit var top: String

    fun safeLen(s: String?): Int = s?.length ?: -1
    fun orDefault(s: String?): String = s ?: "none"
    fun isNull(s: String?) = s == null
    fun isPresent(s: String?) = s != null
    fun force(s: String?): Int { s!!; return 1 }
    fun param(s: String, n: Int): Int = n
    fun wideParams(a: Long, s: String, d: Double): Long = a
    fun platform(): String = System.getProperty("mutkt.absent.property")
    fun platformWide(a: Long): String = java.lang.Long.toString(a)
    fun external(): Int = System.getProperty("mutkt.absent.property")?.length ?: -1
    fun mixed(s: String?, t: String): Int = (s ?: t).length
    fun lateRead(): String = Holder().nameOrFail()
    fun lateInit(): String { val h = Holder(); h.name = "x"; return h.nameOrFail() }
    fun readTop(): String = top
    fun readHidden(): String = hidden
    fun hiddenLength(): Int = hidden.length
    fun ownRead(): String = Own().read()
    fun ownLength(): Int = Own().length()
    fun ownInit(): String = Own().readInit()
    fun plain(a: Int) = a + 1
    fun equal(a: String?, b: String?) = a == b
    """.trimIndent()

class NullSafetyOperatorTest {
    private val classes = compileKotlin(SOURCE)
    private val noLookup: Hierarchy = { null }

    private fun sites(
        method: String,
        className: String = "SourceKt",
    ) = discover(classes.getValue(className), NullSafetyOperator).filter { it.methodName == method }

    private fun mutant(
        method: String,
        ordinal: Int = 0,
        className: String = "SourceKt",
    ): Map<String, ByteArray> {
        val found = sites(method, className).single { it.ordinal == ordinal }
        return classes + (className to mutate(classes.getValue(className), NullSafetyOperator, found, noLookup))
    }

    private fun verify(bytes: ByteArray) =
        StringWriter().also { CheckClassAdapter.verify(ClassReader(bytes), false, PrintWriter(it)) }.toString()

    private fun run(
        method: String,
        vararg args: Any?,
        ordinal: Int = 0,
    ) = loadAndInvoke(mutant(method, ordinal), "SourceKt", method, *args)

    // Runs the static [entry] of SourceKt against the mutant of [method] (ordinal [ordinal]) in [className].
    private fun runVia(
        entry: String,
        method: String,
        ordinal: Int,
        className: String,
    ) = loadAndInvoke(mutant(method, ordinal, className), "SourceKt", entry)

    private fun assertVerifies(
        method: String,
        ordinal: Int = 0,
        className: String = "SourceKt",
    ) = assertEquals("", verify(mutant(method, ordinal, className).getValue(className)))

    @Test
    fun `a safe call flips its IFNULL, so a present value takes the null branch`() {
        assertEquals("ifnull -> ifnonnull", sites("safeLen").single().description)
        assertEquals(3, loadAndInvoke(classes, "SourceKt", "safeLen", "abc"))
        assertEquals(-1, run("safeLen", "abc"))
        assertThrows(NullPointerException::class.java) { run("safeLen", null) }
        assertVerifies("safeLen")
    }

    @Test
    fun `an elvis flips its IFNONNULL`() {
        assertEquals("ifnonnull -> ifnull", sites("orDefault").single().description)
        assertEquals("x", loadAndInvoke(classes, "SourceKt", "orDefault", "x"))
        assertEquals("none", run("orDefault", "x"))
        assertNull(run("orDefault", null))
        assertVerifies("orDefault")
    }

    @Test
    fun `null comparisons flip in both directions`() {
        assertEquals(true, run("isNull", "a"))
        assertEquals(false, run("isNull", null))
        assertEquals(false, run("isPresent", "a"))
        assertEquals(true, run("isPresent", null))
        assertVerifies("isNull")
        assertVerifies("isPresent")
    }

    @Test
    fun `a not-null assertion is removed, so null passes`() {
        assertEquals("remove Intrinsics.checkNotNull", sites("force").single().description)
        assertThrows(NullPointerException::class.java) { loadAndInvoke(classes, "SourceKt", "force", null) }
        assertEquals(1, run("force", null))
        assertVerifies("force")
    }

    @Test
    fun `a parameter check is removed, so null passes`() {
        assertEquals("remove Intrinsics.checkNotNullParameter", sites("param").single().description)
        assertThrows(NullPointerException::class.java) { loadAndInvoke(classes, "SourceKt", "param", null, 7) }
        assertEquals(7, run("param", null, 7))
        assertVerifies("param")
    }

    @Test
    fun `parameter checks in a method with long and double parameters verify`() {
        assertEquals(5L, run("wideParams", 5L, null, 1.0))
        assertVerifies("wideParams")
        assertVerifies("platformWide")
    }

    @Test
    fun `a check on a platform-type result is removed, so null flows on`() {
        assertEquals("remove Intrinsics.checkNotNullExpressionValue", sites("platform").single().description)
        assertThrows(NullPointerException::class.java) { loadAndInvoke(classes, "SourceKt", "platform") }
        assertNull(run("platform"))
        assertVerifies("platform")
    }

    @Test
    fun `a safe call on an external nullable result is a site`() {
        assertEquals(listOf("ifnull -> ifnonnull"), sites("external").map { it.description })
        assertEquals(-1, loadAndInvoke(classes, "SourceKt", "external"))
        assertThrows(NullPointerException::class.java) { run("external") }
    }

    @Test
    fun `a lateinit getter has a flip and a removed uninitialized-access throw`() {
        assertEquals(
            listOf("ifnull -> ifnonnull", "remove Intrinsics.throwUninitializedPropertyAccessException"),
            sites("getName", "Holder").map { it.description },
        )
        assertThrows(RuntimeException::class.java) { loadAndInvoke(classes, "SourceKt", "lateRead") }
        // The compiler follows the call with ACONST_NULL, so the getter returns null instead of throwing.
        assertNull(runVia("lateRead", "getName", 1, "Holder"))
        assertVerifies("getName", 1, "Holder")
    }

    @Test
    fun `flipping a lateinit getter makes an initialized property throw`() {
        assertEquals("x", loadAndInvoke(classes, "SourceKt", "lateInit"))
        assertThrows(RuntimeException::class.java) { runVia("lateInit", "getName", 0, "Holder") }
        assertVerifies("getName", 0, "Holder")
    }

    @Test
    fun `a top-level lateinit getter is mutated the same way`() {
        assertEquals(2, sites("getTop").size)
        assertNull(runVia("readTop", "getTop", 1, "SourceKt"))
        assertVerifies("getTop", 1)
    }

    @Test
    fun `a direct lateinit field read in the owning class verifies and returns null`() {
        val own = sites("read", "Own")
        assertEquals(
            listOf("ifnonnull -> ifnull", "remove Intrinsics.throwUninitializedPropertyAccessException"),
            own.map { it.description },
        )
        assertThrows(RuntimeException::class.java) { loadAndInvoke(classes, "SourceKt", "ownRead") }
        assertNull(runVia("ownRead", "read", 1, "Own"))
        assertVerifies("read", 0, "Own")
        assertVerifies("read", 1, "Own")
        assertEquals("s", loadAndInvoke(classes, "SourceKt", "ownInit"))
    }

    @Test
    fun `a lateinit read followed by a dereference verifies`() {
        sites("length", "Own").forEach { assertVerifies("length", it.ordinal, "Own") }
        sites("readInit", "Own").forEach { assertVerifies("readInit", it.ordinal, "Own") }
        assertThrows(NullPointerException::class.java) { runVia("ownLength", "length", 1, "Own") }
    }

    @Test
    fun `a private top-level lateinit read verifies`() {
        listOf("readHidden", "hiddenLength").forEach { method ->
            assertEquals(true, sites(method).isNotEmpty(), method)
            sites(method).forEach { assertVerifies(method, it.ordinal) }
        }
        assertNull(runVia("readHidden", "readHidden", sites("readHidden").last().ordinal, "SourceKt"))
    }

    @Test
    fun `sites are numbered in instruction order across both kinds`() {
        assertEquals(
            listOf(
                "remove Intrinsics.checkNotNullParameter" to 0,
                "ifnonnull -> ifnull" to 1,
            ),
            sites("mixed").map { it.description to it.ordinal },
        )
        assertVerifies("mixed", 1)
    }

    @Test
    fun `methods without null handling and other Intrinsics calls have no site`() {
        assertEquals(emptyList<Any>(), sites("plain"))
        // Intrinsics.areEqual backs ==, and is not a null check.
        assertEquals(emptyList<Any>(), sites("equal"))
    }

    @Test
    fun `throwNpe is not a site`() {
        val bytes =
            buildClass("Npe", "m") {
                visitMethodInsn(INVOKESTATIC, "kotlin/jvm/internal/Intrinsics", "throwNpe", "()V", false)
                visitInsn(ICONST_0)
                visitInsn(IRETURN)
            }

        assertEquals(emptyList<Any>(), discover(bytes, NullSafetyOperator))
    }
}
