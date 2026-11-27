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
 * Attributes probe hits to test classes: one that a root test class (a container right under its engine) reaches
 * while it runs is that class's; any other (discovery, engine start and stop) is global. Set [testClass] before
 * executing its plan; a plan's time counts as its class's. A class with no root container in its plan (a helper
 * in the test sources) is left out.
 */
internal class CoverageListener(
    private val probes: ProbeReader,
) : TestExecutionListener {
    lateinit var testClass: String
    private lateinit var plan: TestPlan
    private var start = 0L
    private val global = mutableSetOf<Probe>()
    private val hits = LinkedHashMap<String, MutableSet<Probe>>()
    private val elapsed = mutableMapOf<String, Duration>()

    override fun testPlanExecutionStarted(plan: TestPlan) {
        this.plan = plan
        global += probes.drain()
        start = System.nanoTime()
    }

    override fun executionStarted(id: TestIdentifier) {
        if (!isRootClass(id)) return
        global += probes.drain()
        hits.getOrPut(testClass) { mutableSetOf() }
    }

    override fun executionFinished(
        id: TestIdentifier,
        result: TestExecutionResult,
    ) {
        if (isRootClass(id)) hits.getValue(testClass) += probes.drain()
    }

    override fun testPlanExecutionFinished(plan: TestPlan) {
        global += probes.drain()
        if (testClass in hits) elapsed.merge(testClass, (System.nanoTime() - start).nanoseconds, Duration::plus)
    }

    /** What the plans executed so far reached, in execution order. */
    fun coverage(): Coverage {
        global += probes.drain()
        val tests =
            hits.map { (name, reached) ->
                TestCoverage(
                    TestId(name),
                    reached.toSet(),
                    elapsed[name] ?: Duration.ZERO,
                )
            }
        return Coverage(tests, global.toSet())
    }

    private fun isRootClass(id: TestIdentifier) = plan.getParent(id).map { !it.parentId.isPresent }.orElse(false)
}
