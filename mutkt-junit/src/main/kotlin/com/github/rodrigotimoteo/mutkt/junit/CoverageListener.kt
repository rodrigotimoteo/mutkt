package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Coverage
import com.github.rodrigotimoteo.mutkt.application.port.Probe
import com.github.rodrigotimoteo.mutkt.application.port.TestCoverage
import com.github.rodrigotimoteo.mutkt.application.port.TestId
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Attributes probe hits and reached classes to tests: what a Jupiter test method (the outermost identifier with a
 * method source, so a parameterized test is one) reaches while it runs is that method's; what a root test class (a
 * container right under its engine) reaches outside them is that class's, and so are its methods' reached classes;
 * anything else (discovery, engine start and stop) is global. Set [testClass] before executing its plan; a plan's time
 * counts as its class's. A class with no root container in its plan (a helper in the test sources) is left out.
 */
internal class CoverageListener(
    private val probes: ProbeReader,
) : TestExecutionListener {
    lateinit var testClass: String

    /** False records [testClass] as a whole only: its methods cannot run apart (see methodsIndependent). */
    var methodLevel = true
    private lateinit var plan: TestPlan
    private var start = 0L
    private var method: TestId? = null
    private var methodStart = 0L
    private val global = mutableSetOf<Probe>()
    private val globalReach = mutableSetOf<String>()
    private val tests = LinkedHashMap<TestId, Recorded>()

    override fun testPlanExecutionStarted(plan: TestPlan) {
        this.plan = plan
        drainGlobal()
        start = System.nanoTime()
    }

    override fun executionStarted(id: TestIdentifier) {
        val owner = TestId(testClass)
        when {
            isRootClass(id) -> {
                drainGlobal()
                tests.getOrPut(owner) { Recorded() }
            }
            method == null && methodLevel && owner in tests && isJupiterMethod(id) -> {
                tests.getValue(owner) += probes.drain()
                method = TestId(testClass, id.uniqueId).also { tests.getOrPut(it) { Recorded() } }
                methodStart = System.nanoTime()
            }
        }
    }

    override fun executionFinished(
        id: TestIdentifier,
        result: TestExecutionResult,
    ) {
        val open = method
        when {
            open != null && open.method == id.uniqueId -> {
                val drained = probes.drain()
                tests.getValue(open).apply {
                    plusAssign(drained)
                    elapsed += (System.nanoTime() - methodStart).nanoseconds
                }
                // A class's reached classes include its methods': the result history keys on the class's.
                tests.getValue(TestId(testClass)).reached += drained.reached
                method = null
            }
            isRootClass(id) -> tests.getValue(TestId(testClass)) += probes.drain()
        }
    }

    override fun testPlanExecutionFinished(plan: TestPlan) {
        drainGlobal()
        tests[TestId(testClass)]?.let { it.elapsed += (System.nanoTime() - start).nanoseconds }
    }

    /** What the plans executed so far reached, in execution order. */
    fun coverage(): Coverage {
        drainGlobal()
        val tests = tests.map { (test, it) -> TestCoverage(test, it.hits.toSet(), it.elapsed, it.reached.toSet()) }
        return Coverage(tests, global.toSet(), globalReach.toSet())
    }

    private fun drainGlobal() {
        val drained = probes.drain()
        global += drained.hits
        globalReach += drained.reached
    }

    private fun isRootClass(id: TestIdentifier) = plan.getParent(id).map { !it.parentId.isPresent }.orElse(false)

    // Only Jupiter's ids are known to select one method in a fresh launcher; other engines' tests (Kotest, Spek, JUnit
    // 4) stay with their class.
    private fun isJupiterMethod(id: TestIdentifier) =
        id.source.orElse(null) is MethodSource &&
            UniqueId.parse(id.uniqueId).engineId.orElse(null) == JUPITER_ENGINE

    private class Recorded(
        val hits: MutableSet<Probe> = mutableSetOf(),
        val reached: MutableSet<String> = mutableSetOf(),
        var elapsed: Duration = Duration.ZERO,
    ) {
        operator fun plusAssign(drained: Drained) {
            hits += drained.hits
            reached += drained.reached
        }
    }
}
