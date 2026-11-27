package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import com.github.rodrigotimoteo.mutkt.testing.TEST_PROBE_REGISTRY
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class LazyInitialiserProbesTest {
    // One lazy per line, so a mutant's line tells which lambda holds it.
    private val source =
        """
        fun two(): Int = 2
        class C(private val n: Int) { val a by lazy { n + 1 } }
        object O {
            val o by lazy { two() + 3 }
            val p = lazy(LazyThreadSafetyMode.NONE) { two() * 4 }
        }
        class K { companion object { val k by lazy { two() - 5 } } }
        val top by lazy { two() / 6 }
        fun normal(x: Int): () -> Int = { x + 7 }
        """.trimIndent()

    @ParameterizedTest
    // indy: kotlinc 2 compiles a lambda to a method of its class; class: kotlinc 1.x compiles it to a class of its own.
    @ValueSource(strings = ["indy", "class"])
    fun `a lazy created while a class initialises is unprobed, other lambdas are probed`(lambdas: String) {
        val classes = compileKotlin(source, kotlincArguments = listOf("-Xlambdas=$lambdas"))
        val files = classes.map { ClassFile(it.key, it.value) }
        val mutants = AsmMutantGenerator(::error) { null }.discover(files, files)

        val probes = AsmCoverageInstrumenter(TEST_PROBE_REGISTRY) { null }.instrument(files, mutants).probes

        // Each lambda body holds the line's only arithmetic site.
        assertEquals(
            mapOf(2 to true, 4 to false, 5 to false, 7 to false, 8 to false, 9 to true),
            mutants.filter { it.operator == OperatorId("arithmetic") }.associate { it.line to (it.id in probes) },
        )
    }
}
