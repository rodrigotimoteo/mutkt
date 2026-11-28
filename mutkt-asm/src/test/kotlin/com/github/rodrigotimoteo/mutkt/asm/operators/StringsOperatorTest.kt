package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Found
import com.github.rodrigotimoteo.mutkt.asm.Hierarchy
import com.github.rodrigotimoteo.mutkt.asm.Operators
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.testing.IsolatedClasses
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter

// A dollar sign: templates in SOURCE are written as ${D}name.
private const val D = "$"

private val SOURCE =
    """
    fun greet(a: Int, b: String?) = "a=${D}a b=${D}b!"
    fun wide(a: Long, d: Double, c: Char) = "x$D{a}y$D{d}z${D}c"
    fun fallback(b: String?) = "v=$D{b ?: "none"}"
    fun word() = "hello"
    fun blank() = ""
    fun long() = "a message well over thirty characters long"
    fun fail(n: Int): Int {
        if (n < 0) throw IllegalArgumentException("negative: ${D}n")
        return n
    }
    fun failure(n: Int): String? = try { fail(n); null } catch (e: IllegalArgumentException) { e.message }
    fun built(a: Int) = buildString { append("n="); append(a) }
    fun later(n: Int): String {
        val f = { "n=${D}n" }
        return f()
    }

    // Literals used as keys.
    annotation class Tag(val v: String)
    @Tag("key") fun tagged() = 1
    fun property(): String? = System.getProperty("mutkt.absent")
    fun type(): Class<*> = Class.forName("java.lang.String")
    fun label(s: String) = when (s) { "a" -> 1; "b" -> 2; "c" -> 3; else -> 0 }
    fun same(s: String) = s == "x"

    object Messages {
        class Greeter(private val name: String) {
            fun hi() = "hi ${D}name"
            fun hi(times: Int) = "hi ${D}name x${D}times"
        }
    }
    fun hiOnce(n: String) = Messages.Greeter(n).hi()
    fun hiTimes(n: String, t: Int) = Messages.Greeter(n).hi(t)
    """.trimIndent()

// kotlinc compiles templates to invokedynamic StringConcatFactory on JVM 9+ targets, and to StringBuilder appends
// with -Xstring-concat=inline (the only form for JVM 1.8 targets).
internal enum class Concat(
    val arguments: List<String>,
) {
    INDY(emptyList()),
    BUILDER(listOf("-Xstring-concat=inline")),
    ;

    val classes by lazy { compileKotlin(SOURCE, kotlincArguments = arguments) }
}

internal class StringsOperatorTest {
    private val noLookup: Hierarchy = { null }

    private fun sites(
        form: Concat,
        method: String,
        cls: String = "SourceKt",
    ) = discover(form.classes.getValue(cls), StringsOperator).filter { it.methodName == method }

    private fun mutant(
        form: Concat,
        found: Found,
    ): Map<String, ByteArray> {
        val bytes = mutate(form.classes.getValue(found.className), StringsOperator, found, noLookup)
        val loader = IsolatedClasses(form.classes).type("SourceKt").classLoader
        val log = StringWriter().also { CheckClassAdapter.verify(ClassReader(bytes), loader, false, PrintWriter(it)) }
        assertEquals("", log.toString(), "$found produced an invalid class")
        return form.classes + (found.className to bytes)
    }

    // The result of [call] with [args] under each site of [method], in site order.
    private fun results(
        form: Concat,
        method: String,
        call: String = method,
        vararg args: Any?,
    ) = sites(form, method).map { loadAndInvoke(mutant(form, it), "SourceKt", call, *args) }

    private fun call(
        form: Concat,
        method: String,
        vararg args: Any?,
    ) = loadAndInvoke(form.classes, "SourceKt", method, *args)

    @ParameterizedTest
    @EnumSource(Concat::class)
    fun `each part of a template is dropped in turn`(form: Concat) {
        assertEquals("a=1 b=x!", call(form, "greet", 1, "x"))
        assertEquals(
            listOf("1 b=x!", "a= b=x!", "a=1x!", "a=1 b=!", "a=1 b=x"),
            results(form, "greet", "greet", 1, "x"),
        )
    }

    @ParameterizedTest
    @EnumSource(Concat::class)
    fun `long and double values keep the stack balanced`(form: Concat) {
        assertEquals("x4y2.5zc", call(form, "wide", 4L, 2.5, 'c'))
        assertEquals(
            listOf("4y2.5zc", "xy2.5zc", "x42.5zc", "x4yzc", "x4y2.5c", "x4y2.5z"),
            results(form, "wide", "wide", 4L, 2.5, 'c'),
        )
    }

    @Test
    fun `descriptions name the part or the append`() {
        assertEquals(
            listOf(
                "concat part 1/5 \"a=\" -> dropped",
                "concat part 2/5 (int) -> dropped",
                "concat part 3/5 \" b=\" -> dropped",
                "concat part 4/5 (String) -> dropped",
                "concat part 5/5 \"!\" -> dropped",
            ),
            sites(Concat.INDY, "greet").map { it.description },
        )
        assertEquals(
            listOf("String", "int", "String", "String", "char").map { "StringBuilder.append($it) -> removed" },
            sites(Concat.BUILDER, "greet").map { it.description },
        )
    }

    @ParameterizedTest
    @EnumSource(Concat::class)
    fun `a literal becomes empty unless it is a part of a concatenation`(form: Concat) {
        assertEquals("hello" to listOf(""), call(form, "word") to results(form, "word"))
        assertEquals(listOf("\"hello\" -> \"\""), sites(form, "word").map { it.description })
        assertEquals(
            "\"a message well over thirty cha...\" -> \"\"",
            sites(form, "long").single().description,
        )
        assertTrue(sites(form, "blank").isEmpty())
        // The elvis default reaches the concatenation through a branch, so it is a literal of its own.
        assertEquals("v=none", call(form, "fallback", null))
        assertEquals(
            listOf("none", "v=", "v="),
            results(form, "fallback", "fallback", null).map { it as String }.sorted(),
        )
    }

    @ParameterizedTest
    @EnumSource(Concat::class)
    fun `exception messages and builder appends are sites`(form: Concat) {
        assertEquals("negative: -1", call(form, "failure", -1))
        assertEquals(listOf("-1", "negative: "), results(form, "fail", "failure", -1))
        assertEquals("n=3" to listOf("3", "n="), call(form, "built", 3) to results(form, "built", "built", 3))
    }

    @ParameterizedTest
    @EnumSource(Concat::class)
    fun `a lambda body has its own sites`(form: Concat) {
        val lambda = discover(form.classes.getValue("SourceKt"), StringsOperator).filter { "lambda" in it.methodName }

        assertEquals(2, lambda.size)
        assertEquals(
            listOf("2", "n="),
            lambda.map { loadAndInvoke(mutant(form, it), "SourceKt", "later", 2) },
        )
        assertTrue(sites(form, "later").isEmpty(), "the lambda factory is not a concatenation")
    }

    @ParameterizedTest
    @EnumSource(Concat::class)
    fun `keys and compiler names are not sites`(form: Concat) {
        val keyed = listOf("tagged", "property", "type", "label", "same")

        assertEquals(emptyList<Found>(), keyed.flatMap { sites(form, it) })
    }

    @ParameterizedTest
    @EnumSource(Concat::class)
    fun `overloads in a nested class are separate sites`(form: Concat) {
        val cls = "Messages\$Greeter"
        val hi = sites(form, "hi", cls)
        assertEquals(
            listOf("()Ljava/lang/String;" to 0, "()Ljava/lang/String;" to 1) +
                (0..3).map { "(I)Ljava/lang/String;" to it },
            hi.map { it.descriptor to it.ordinal },
        )
        val dropped = mutant(form, hi.first { it.descriptor == "()Ljava/lang/String;" && it.ordinal == 1 })
        assertEquals(
            "hi " to "hi bob x2",
            loadAndInvoke(dropped, "SourceKt", "hiOnce", "bob") to
                loadAndInvoke(dropped, "SourceKt", "hiTimes", "bob", 2),
        )
    }

    @Test
    fun `the operator is opt-in`() {
        assertFalse(StringsOperator in Operators.defaults)
        assertEquals(listOf(StringsOperator), Operators.byId(setOf("strings")))
    }
}
