package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.asm.operators.ArithmeticOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.ConditionalsBoundaryOperator
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SuppressionTest {
    // Explicit so the tests do not change when operators are added to Operators.all.
    private val operators = listOf(ArithmeticOperator, ConditionalsBoundaryOperator)

    private fun filesOf(source: String) =
        compileKotlin("import com.github.rodrigotimoteo.mutkt.annotations.SuppressMutations\n$source", true)
            .map { (name, bytes) -> ClassFile(name, bytes) }

    private fun operatorsByMethod(source: String): Map<String, Set<String>> =
        AsmMutantGenerator(operators) { null }
            .discover(filesOf(source))
            .groupBy({ "${it.className}.${it.methodName}" }, { it.operator.value })
            .mapValues { it.value.toSet() }

    private val both = setOf("arithmetic", "conditionals-boundary")

    @Test
    fun `no annotation leaves every operator on`() {
        val result = operatorsByMethod("class C { fun f(a: Int) = if (a > 1) a + 1 else 0 }")

        assertEquals(mapOf("C.f" to both), result)
    }

    @Test
    fun `class level without operators suppresses everything`() {
        val result =
            operatorsByMethod(
                """
                @SuppressMutations(reason = "generated")
                class C { fun f(a: Int) = if (a > 1) a + 1 else 0 }
                class D { fun g(a: Int) = a + 1 }
                """.trimIndent(),
            )

        assertEquals(mapOf("D.g" to setOf("arithmetic")), result)
    }

    @Test
    fun `class level with an operator keeps the others`() {
        val result =
            operatorsByMethod(
                """
                @SuppressMutations(["arithmetic"])
                class C { fun f(a: Int) = if (a > 1) a + 1 else 0 }
                """.trimIndent(),
            )

        assertEquals(mapOf("C.f" to setOf("conditionals-boundary")), result)
    }

    @Test
    fun `method level only affects that method`() {
        val result =
            operatorsByMethod(
                """
                class C {
                    @SuppressMutations(["conditionals-boundary"])
                    fun f(a: Int) = if (a > 1) a + 1 else 0
                    fun g(a: Int) = if (a > 1) a + 1 else 0
                }
                """.trimIndent(),
            )

        assertEquals(mapOf("C.f" to setOf("arithmetic"), "C.g" to both), result)
    }

    @Test
    fun `class and method suppression are unioned`() {
        val result =
            operatorsByMethod(
                """
                @SuppressMutations(["arithmetic"])
                class C {
                    @SuppressMutations(["conditionals-boundary"])
                    fun f(a: Int) = if (a > 1) a + 1 else 0
                    fun g(a: Int) = if (a > 1) a + 1 else 0
                }
                """.trimIndent(),
            )

        assertEquals(mapOf("C.g" to setOf("conditionals-boundary")), result)
    }

    @Test
    fun `nested and anonymous classes inherit the enclosing suppression`() {
        val result =
            operatorsByMethod(
                """
                @SuppressMutations(["arithmetic"])
                class Outer {
                    class Nested { fun f(a: Int) = a + 1 }
                    inner class Inner { fun g(a: Int) = a + 1 }
                    fun h(): Runnable = object : Runnable { override fun run() { check(System.nanoTime() + 1 > 0) } }
                }
                class Free { class Nested { fun f(a: Int) = a + 1 } }
                """.trimIndent(),
            )

        assertEquals(
            mapOf("Free\$Nested.f" to setOf("arithmetic"), "Outer\$h\$1.run" to setOf("conditionals-boundary")),
            result,
        )
    }

    @Test
    fun `anonymous class inside a suppressed method inherits the method suppression`() {
        val result =
            operatorsByMethod(
                """
                class C {
                    @SuppressMutations
                    fun h(): Runnable = object : Runnable { override fun run() { check(System.nanoTime() + 1 > 0) } }
                    fun k(): Runnable = object : Runnable { override fun run() { check(System.nanoTime() + 1 > 0) } }
                }
                """.trimIndent(),
            )

        assertEquals(setOf("C\$k\$1.run"), result.keys)
    }

    @Test
    fun `lambdas of a suppressed function are suppressed with it`() {
        val result =
            operatorsByMethod(
                """
                class C {
                    @SuppressMutations
                    fun f(n: Int): () -> Int = { val inner: () -> Int = { n + 2 }; inner() + n + 1 }
                    fun g(n: Int): () -> Int = { n + 1 }
                }
                """.trimIndent(),
            )

        assertEquals(1, result.size)
        assertTrue(result.keys.single().startsWith("C.g\$lambda\$"), result.keys.toString())
    }

    @Test
    fun `annotating one overload leaves its siblings mutated`() {
        val mutants =
            AsmMutantGenerator(operators) { null }.discover(
                filesOf(
                    """
                    class C {
                        @SuppressMutations
                        fun f(a: Int) = a + 1
                        fun f(a: Long) = a + 1
                    }
                    """.trimIndent(),
                ),
            )

        assertEquals(listOf("(J)J"), mutants.map { it.methodDescriptor })
    }

    @Test
    fun `file level functions are suppressed individually`() {
        val result =
            operatorsByMethod(
                """
                @SuppressMutations
                fun top(a: Int) = a + 1
                fun other(a: Int) = a + 1
                """.trimIndent(),
            )

        assertEquals(mapOf("SourceKt.other" to setOf("arithmetic")), result)
    }

    @Test
    fun `companion objects follow their class and their own methods`() {
        val result =
            operatorsByMethod(
                """
                @SuppressMutations
                class Off { companion object { fun f(a: Int) = a + 1 } }
                class On {
                    companion object {
                        @SuppressMutations
                        fun f(a: Int) = a + 1
                        fun g(a: Int) = a + 1
                    }
                }
                """.trimIndent(),
            )

        assertEquals(mapOf("On\$Companion.g" to setOf("arithmetic")), result)
    }

    @Test
    fun `unknown operator names fail with the valid names`() {
        val error =
            assertThrows<IllegalArgumentException> {
                operatorsByMethod("class C { @SuppressMutations([\"arithmetik\"]) fun f(a: Int) = a + 1 }")
            }

        val message = error.message.orEmpty()
        assertTrue("arithmetik" in message && "C.f" in message, message)
        assertTrue(Operators.all.all { it.id.value in message }, message)
    }

    private fun mutantsOf(
        selection: Set<String>,
        source: String,
    ) = AsmMutantGenerator(selection) { null }.discover(filesOf(source)).map { it.operator.value to it.methodName }

    @Test
    fun `a suppression naming an operator outside the selection is valid`() {
        val source =
            """
            class C {
                @SuppressMutations(["null-returns"])
                fun f(a: Int) = a + 1
            }
            """.trimIndent()

        assertEquals(listOf("arithmetic" to "f"), mutantsOf(setOf("arithmetic"), source))
    }

    @Test
    fun `an empty selection accepts a suppression naming any catalog operator`() {
        val mutants =
            mutantsOf(
                emptySet(),
                """
                @SuppressMutations(["coroutine"])
                class C { fun f(a: Int) = a + 1 }
                """.trimIndent(),
            )

        assertTrue(("arithmetic" to "f") in mutants, mutants.toString())
    }

    @Test
    fun `enabling an operator honors its suppression`() {
        val source =
            """
            class C {
                @SuppressMutations(["null-returns"])
                fun off(): String? = "x"
                fun on(): String? = "x"
            }
            """.trimIndent()

        assertEquals(listOf("null-returns" to "on"), mutantsOf(setOf("null-returns"), source))
    }

    @Test
    fun `a typo fails even when the selection excludes every operator that would match`() {
        val source = "class C { @SuppressMutations([\"null-return\"]) fun f(a: Int) = a + 1 }"

        val error = assertThrows<IllegalArgumentException> { mutantsOf(setOf("arithmetic"), source) }

        val message = error.message.orEmpty()
        assertTrue("'null-return'" in message && "null-returns" in message && "coroutine" in message, message)
    }
}
