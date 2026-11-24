package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Hierarchy
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.asm.parse
import com.github.rodrigotimoteo.mutkt.testing.buildClass
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.ICONST_1
import org.objectweb.asm.Opcodes.IFEQ
import org.objectweb.asm.Opcodes.IFGE
import org.objectweb.asm.Opcodes.IFGT
import org.objectweb.asm.Opcodes.IFLE
import org.objectweb.asm.Opcodes.IFLT
import org.objectweb.asm.Opcodes.IFNE
import org.objectweb.asm.Opcodes.IFNONNULL
import org.objectweb.asm.Opcodes.IFNULL
import org.objectweb.asm.Opcodes.IF_ACMPEQ
import org.objectweb.asm.Opcodes.IF_ACMPNE
import org.objectweb.asm.Opcodes.IF_ICMPEQ
import org.objectweb.asm.Opcodes.IF_ICMPGE
import org.objectweb.asm.Opcodes.IF_ICMPGT
import org.objectweb.asm.Opcodes.IF_ICMPLE
import org.objectweb.asm.Opcodes.IF_ICMPLT
import org.objectweb.asm.Opcodes.IF_ICMPNE
import org.objectweb.asm.Opcodes.IRETURN
import java.util.stream.Stream

private val SOURCE =
    """
    fun isTrue(b: Boolean) = if (b) 1 else 0
    fun isFalse(b: Boolean) = if (!b) 1 else 0
    fun ltZero(a: Int) = if (a < 0) 1 else 0
    fun geZero(a: Int) = if (a >= 0) 1 else 0
    fun gtZero(a: Int) = if (a > 0) 1 else 0
    fun leZero(a: Int) = if (a <= 0) 1 else 0
    fun eq(a: Int, b: Int) = if (a == b) 1 else 0
    fun ne(a: Int, b: Int) = if (a != b) 1 else 0
    fun lt(a: Int, b: Int) = if (a < b) 1 else 0
    fun ge(a: Int, b: Int) = if (a >= b) 1 else 0
    fun gt(a: Int, b: Int) = if (a > b) 1 else 0
    fun le(a: Int, b: Int) = if (a <= b) 1 else 0
    fun same(a: Any, b: Any) = if (a === b) 1 else 0
    fun different(a: Any, b: Any) = if (a !== b) 1 else 0
    fun ltLong(a: Long, b: Long) = if (a < b) 1 else 0
    fun gtDouble(a: Double, b: Double) = if (a > b) 1 else 0
    fun clamp(a: Int): Int {
        if (a < 0) return 0
        if (a > 9) return 9
        return a
    }
    fun isNull(a: String?) = if (a == null) 1 else 0
    fun notNull(a: String?) = if (a != null) 1 else 0
    fun plain(a: Int, b: Int) = a + b
    fun twice(n: Int): Int {
        var i = 0
        var sum = 0
        while (i < n) {
            sum += 2
            i++
        }
        return sum
    }
    class Box(private val limit: Int) {
        fun over(a: Int) = a > limit
        fun over(a: Long) = a > limit
    }
    """.trimIndent()

private val classes by lazy { compileKotlin(SOURCE) }
private val hierarchy: Hierarchy = { classes[it.replace('/', '.')] }
private val jumps =
    listOf(
        IFEQ,
        IFNE,
        IFLT,
        IFGE,
        IFGT,
        IFLE,
        IF_ICMPEQ,
        IF_ICMPNE,
        IF_ICMPLT,
        IF_ICMPGE,
        IF_ICMPGT,
        IF_ICMPLE,
        IF_ACMPEQ,
        IF_ACMPNE,
        IFNULL,
        IFNONNULL,
    )

private fun opcodes(
    bytes: ByteArray,
    method: String,
) = parse(bytes)
    .methods
    .single { it.name == method }
    .instructions
    .map { it.opcode }
    .filter { it in jumps }

class NegateConditionalsOperatorTest {
    private val original = classes.getValue("SourceKt")

    private fun mutantOf(
        method: String,
        ordinal: Int = 0,
    ) = discover(original, NegateConditionalsOperator)
        .filter { it.methodName == method }
        .let { mutate(original, NegateConditionalsOperator, it[ordinal], hierarchy) }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `flips the opcode and the loaded mutant behaves differently`(
        method: String,
        from: Int,
        to: Int,
        args: Array<Any>,
    ) {
        val mutant = mutantOf(method)

        assertEquals(listOf(from), opcodes(original, method))
        assertEquals(listOf(to), opcodes(mutant, method))
        assertNotEquals(
            loadAndInvoke(classes, "SourceKt", method, *args),
            loadAndInvoke(classes + ("SourceKt" to mutant), "SourceKt", method, *args),
        )
    }

    @Test
    fun `ordinal selects among several sites of one method`() {
        val sites = discover(original, NegateConditionalsOperator).filter { it.methodName == "clamp" }
        val before = opcodes(original, "clamp")

        assertEquals(listOf(0, 1), sites.map { it.ordinal })
        assertEquals(listOf(IFGE, IF_ICMPLE), before)
        assertEquals(listOf(IFGE, IF_ICMPGT), opcodes(mutantOf("clamp", 1), "clamp"))
        assertEquals(listOf(IFLT, IF_ICMPLE), opcodes(mutantOf("clamp", 0), "clamp"))
    }

    @Test
    fun `overloads are mutated by descriptor`() {
        val sites = discover(classes.getValue("Box"), NegateConditionalsOperator)

        assertEquals(listOf("(I)Z", "(J)Z"), sites.map { it.descriptor }.sorted())
        assertEquals(listOf(0, 0), sites.map { it.ordinal })
    }

    @Test
    fun `description names both opcodes`() {
        val site = discover(original, NegateConditionalsOperator).first { it.methodName == "eq" }

        assertEquals("if_icmpne -> if_icmpeq", site.description)
    }

    @Test
    fun `kotlin null checks are not sites`() {
        val sites = discover(original, NegateConditionalsOperator)

        assertTrue(opcodes(original, "isNull").single() in listOf(IFNULL, IFNONNULL))
        assertTrue(opcodes(original, "notNull").single() in listOf(IFNULL, IFNONNULL))
        assertTrue(sites.none { it.methodName == "isNull" || it.methodName == "notNull" })
    }

    @Test
    fun `negating a while guard exits the loop before the body`() {
        val mutant = mutantOf("twice")

        assertEquals(listOf(IF_ICMPGE), opcodes(original, "twice"))
        assertEquals(listOf(IF_ICMPLT), opcodes(mutant, "twice"))
        assertEquals(6, loadAndInvoke(classes, "SourceKt", "twice", 3))
        assertEquals(0, loadAndInvoke(classes + ("SourceKt" to mutant), "SourceKt", "twice", 3))
    }

    @Test
    fun `methods without a conditional have no site`() {
        assertTrue(discover(original, NegateConditionalsOperator).none { it.methodName == "plain" })
    }

    @ParameterizedTest
    @MethodSource("nullJumps")
    fun `IFNULL and IFNONNULL are never sites, even without kotlin metadata`(opcode: Int) {
        val bytes =
            buildClass("JavaLike", "m", "(Ljava/lang/Object;)I") {
                val target = Label()
                visitVarInsn(ALOAD, 0)
                visitJumpInsn(opcode, target)
                visitInsn(ICONST_0)
                visitInsn(IRETURN)
                visitLabel(target)
                visitInsn(ICONST_1)
                visitInsn(IRETURN)
            }

        assertEquals(listOf(opcode), opcodes(bytes, "m"))
        assertTrue(discover(bytes, NegateConditionalsOperator).isEmpty())
    }

    companion object {
        @JvmStatic
        fun nullJumps(): Stream<Arguments> = Stream.of(Arguments.of(IFNULL), Arguments.of(IFNONNULL))

        @JvmStatic
        fun cases(): Stream<Arguments> =
            Stream.of(
                case("isTrue", IFEQ, IFNE, true),
                case("isFalse", IFNE, IFEQ, true),
                case("ltZero", IFGE, IFLT, -1),
                case("geZero", IFLT, IFGE, 1),
                case("gtZero", IFLE, IFGT, 1),
                case("leZero", IFGT, IFLE, 0),
                case("eq", IF_ICMPNE, IF_ICMPEQ, 1, 1),
                case("ne", IF_ICMPEQ, IF_ICMPNE, 1, 2),
                case("lt", IF_ICMPGE, IF_ICMPLT, 1, 2),
                case("ge", IF_ICMPLT, IF_ICMPGE, 2, 2),
                case("gt", IF_ICMPLE, IF_ICMPGT, 3, 2),
                case("le", IF_ICMPGT, IF_ICMPLE, 2, 2),
                case("same", IF_ACMPNE, IF_ACMPEQ, "a", "a"),
                case("different", IF_ACMPEQ, IF_ACMPNE, "a", "b"),
                case("ltLong", IFGE, IFLT, 1L, 2L),
                case("gtDouble", IFLE, IFGT, 2.0, 1.0),
            )

        private fun case(
            method: String,
            from: Int,
            to: Int,
            vararg args: Any,
        ) = Arguments.of(method, from, to, args)
    }
}
