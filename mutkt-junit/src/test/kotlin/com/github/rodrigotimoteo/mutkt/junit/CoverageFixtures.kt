package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.Instrumented
import com.github.rodrigotimoteo.mutkt.application.port.Probe
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin

internal const val PLAIN_CALC = "class Calc { fun add(a: Int, b: Int) = a + b\nfun sub(a: Int, b: Int) = a - b }"

// Calc with probes the way AsmCoverageInstrumenter adds them (mutkt-junit cannot use mutkt-asm): a static boolean[]
// created on the first hit. add sets probe 0, sub probe 1.
private const val PROBED_CALC =
    """
    class Calc {
        fun add(a: Int, b: Int): Int { hit(0); return a + b }
        fun sub(a: Int, b: Int): Int { hit(1); return a - b }
        companion object {
            @JvmField var probes: BooleanArray? = null
            fun hit(index: Int) { (probes ?: BooleanArray(2).also { probes = it })[index] = true }
        }
    }
    """

internal val ADD = Probe("Calc", 0)
internal val SUB = Probe("Calc", 1)

/** Project classes (plain Calc, plus what the probed one needs), test classes and the probed Calc. */
internal data class CoverageFixture(
    val project: List<ClassFile>,
    val tests: List<ClassFile>,
    val instrumented: Instrumented,
)

/** Compiles [tests] (Kotlin after the imports and a plain Calc) and [extra] project classes, probed ones included. */
internal fun coverageFixture(
    tests: String,
    extra: String = "",
    probed: Set<String> = emptySet(),
): CoverageFixture {
    val production = setOf("Calc") + compileKotlin(extra).keys
    val (project, testClasses) = splitClasses("$IMPORTS$PLAIN_CALC\n$extra\n$tests", production)
    val probedCalc = compileKotlin(PROBED_CALC).map { ClassFile(it.key, it.value) }
    val (calc, companion) = probedCalc.partition { it.name == "Calc" }
    val instrumented = Instrumented(calc + project.filter { it.name in probed }, "probes", emptyMap())
    return CoverageFixture(project + companion, testClasses, instrumented)
}
