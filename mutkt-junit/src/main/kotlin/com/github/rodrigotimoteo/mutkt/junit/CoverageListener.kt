package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Coverage
import com.github.rodrigotimoteo.mutkt.application.port.Probe
import com.github.rodrigotimoteo.mutkt.application.port.TestCoverage
import com.github.rodrigotimoteo.mutkt.application.port.TestId
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Attributes probe hits and reached classes to test classes: what a root test class (a container right under its
 * engine) reaches while it runs is that class's; anything else (discovery, engine start and stop) is global. Set
 * [testClass] before executing its plan; a plan's time counts as its class's. A class with no root container in its
 * plan (a helper in the test sources) is left out.
 */
internal class CoverageListener(
    private val probes: ProbeReader,
) : TestExecutionListener {
    lateinit var testClass: String
    private lateinit var plan: TestPlan
    private var start = 0L
    private val global = mutableSetOf<Probe>()
    private val globalReach = mutableSetOf<String>()
    private val hits = LinkedHashMap<String, MutableSet<Probe>>()
    private val reached = mutableMapOf<String, MutableSet<String>>()
    private val elapsed = mutableMapOf<String, Duration>()

    override fun testPlanExecutionStarted(plan: TestPlan) {
        this.plan = plan
        drainGlobal()
        start = System.nanoTime()
    }

    override fun executionStarted(id: TestIdentifier) {
        if (!isRootClass(id)) return
        drainGlobal()
        hits.getOrPut(testClass) { mutableSetOf() }
        reached.getOrPut(testClass) { mutableSetOf() }
    }

    override fun executionFinished(
        id: TestIdentifier,
        result: TestExecutionResult,
    ) {
        if (!isRootClass(id)) return
        val drained = probes.drain()
        hits.getValue(testClass) += drained.hits
        reached.getValue(testClass) += drained.reached
    }

    override fun testPlanExecutionFinished(plan: TestPlan) {
        drainGlobal()
        if (testClass in hits) elapsed.merge(testClass, (System.nanoTime() - start).nanoseconds, Duration::plus)
    }

    /** What the plans executed so far reached, in execution order. */
    fun coverage(): Coverage {
        drainGlobal()
        val tests =
            hits.map { (name, hit) ->
                TestCoverage(
                    TestId(name),
                    hit.toSet(),
                    elapsed[name] ?: Duration.ZERO,
                    reached.getValue(name).toSet(),
                )
            }
        return Coverage(tests, global.toSet(), globalReach.toSet())
    }

    private fun drainGlobal() {
        val drained = probes.drain()
        global += drained.hits
        globalReach += drained.reached
    }

    private fun isRootClass(id: TestIdentifier) = plan.getParent(id).map { !it.parentId.isPresent }.orElse(false)
}
