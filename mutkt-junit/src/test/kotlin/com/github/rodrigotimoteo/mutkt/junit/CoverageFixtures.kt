package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.Instrumented
import com.github.rodrigotimoteo.mutkt.application.port.Probe
import com.github.rodrigotimoteo.mutkt.application.port.TestId
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin

internal const val PLAIN_CALC = "class Calc { fun add(a: Int, b: Int) = a + b\nfun sub(a: Int, b: Int) = a - b }"

// Calc with probes the way AsmCoverageInstrumenter adds them (mutkt-junit cannot use mutkt-asm): an array from the
// registry, reached through the system class loader. add sets probe 0, sub probe 1, both the entry slot (the last).
private const val PROBED_CALC =
    """
    class Calc {
        fun add(a: Int, b: Int): Int { hit(2); hit(0); return a + b }
        fun sub(a: Int, b: Int): Int { hit(2); hit(1); return a - b }
        private fun hit(index: Int) {
            val registry = ClassLoader.getSystemClassLoader().loadClass("$PROBE_REGISTRY")
            val probes = registry.getMethod("probes", String::class.java, Integer.TYPE).invoke(null, "Calc", 3)
            (probes as BooleanArray)[index] = true
        }
    }
    """

// ATest adds in a copy of Calc that a second loader defines from its class file, as Robolectric's sandbox does.
internal const val SANDBOX_TEST =
    "class ATest { @Test fun adds() {\n" +
        "val sandbox = com.github.rodrigotimoteo.mutkt.testing\n" +
        ".sandboxLoader(Calc::class.java.classLoader, setOf(\"Calc\"))\n" +
        "val calc = sandbox.loadClass(\"Calc\")\n" +
        "assertNotSame(Calc::class.java, calc)\n" +
        "val add = calc.getMethod(\"add\", Integer.TYPE, Integer.TYPE)\n" +
        "assertEquals(5, add.invoke(calc.getDeclaredConstructor().newInstance(), 2, 3)) } }"

/** Jupiter's id of test method [name] (no parameters) of root class [testClass]. */
internal fun method(
    testClass: String,
    name: String,
) = TestId(testClass, "[engine:junit-jupiter]/[class:$testClass]/[method:$name()]")

internal val ADD = Probe("Calc", 0)
internal val SUB = Probe("Calc", 1)

/** Project classes (plain Calc), test classes and the probed Calc. */
internal data class CoverageFixture(
    val project: List<ClassFile>,
    val tests: List<ClassFile>,
    val instrumented: Instrumented,
)

/** Compiles [tests] (Kotlin after the imports and a plain Calc). */
internal fun coverageFixture(tests: String): CoverageFixture {
    val (project, testClasses) = splitClasses("$IMPORTS$PLAIN_CALC\n$tests")
    val calc = ClassFile("Calc", compileKotlin(PROBED_CALC).getValue("Calc"))
    return CoverageFixture(project, testClasses, Instrumented(listOf(calc), emptyMap()))
}
