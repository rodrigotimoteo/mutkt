package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.asm.AsmCoverageInstrumenter
import com.github.rodrigotimoteo.mutkt.asm.AsmMutantGenerator
import com.github.rodrigotimoteo.mutkt.junit.JUnitTestExecutor
import com.github.rodrigotimoteo.mutkt.junit.PROBE_REGISTRY
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/** The instrumenter's probes read by the JUnit executor: which main classes each test class reaches. */
class ClassReachTest {
    @Test
    fun `a test class reaching a class or interface only through a method without mutation sites lists it`() {
        val compiled = compileKotlin(SOURCE, inheritClassPath = true).map { ClassFile(it.key, it.value) }
        val (tests, production) = compiled.partition { it.name.endsWith("Test") }
        val mutants = AsmMutantGenerator(::error, ::systemClass).discover(production, production)
        val instrumented = AsmCoverageInstrumenter(PROBE_REGISTRY, ::systemClass).instrument(production, mutants)

        val baseline = JUnitTestExecutor(production, tests, runtimeJars()).use { it.baseline(60.seconds, instrumented) }

        assertTrue(mutants.none { it.className == "Greeter" })
        assertEquals(
            mapOf("CalcTest" to setOf("Calc"), "GreeterTest" to setOf("Greeter", "Named")),
            baseline.coverage!!.tests.associate { it.test.className to it.reached },
        )
    }

    private companion object {
        val SOURCE =
            """
            import org.junit.jupiter.api.Assertions.assertEquals
            import org.junit.jupiter.api.Test

            class Calc { fun add(a: Int, b: Int) = a + b }
            interface Named { fun name() = "greeter" }
            class Greeter : Named { fun greet() {} }
            class CalcTest { @Test fun adds() { assertEquals(5, Calc().add(2, 3)) } }
            class GreeterTest { @Test fun greets() { Greeter().greet(); Greeter().name() } }
            """.trimIndent()
    }
}
