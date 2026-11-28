package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.gradle.AndroidBuild.Companion.JUPITER_JARS
import com.github.rodrigotimoteo.mutkt.gradle.AndroidBuild.Companion.REPOSITORIES
import com.github.rodrigotimoteo.mutkt.gradle.TestKitProject.Companion.GATE
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** The survivor baseline on real AGP; run by `agpTest`. */
@Tag("agp")
class MutKtAndroidBaselineTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `mutktUpdateBaseline reads the variant's classes from AGP and mutkt accepts what it wrote`() {
        val android = AndroidBuild(dir)
        android.setUp(
            """
            $REPOSITORIES
            android { testOptions { unitTests.all { useJUnitPlatform() } } }
            dependencies { testImplementation files($JUPITER_JARS) }
            mutkt { operators = ['conditionals-boundary']; failOnNewSurvivors = true }
            """.trimIndent(),
        )
        android.write("src/main/java/demo/Gate.java", GATE)
        android.write("src/test/java/demo/GateTest.java", GATE_TEST)

        val update = android.runner("mutktUpdateBaseline").build()
        val check = android.runner("mutkt").build()

        assertEquals(TaskOutcome.SUCCESS, update.task(":mutktUpdateBaseline")?.outcome, update.output)
        // Without AGP's class inputs there would be no mutant, so the baseline would be empty.
        val baseline = dir.resolve("mutkt-baseline.json").readText()
        assertTrue("conditionals-boundary:demo.Gate#isAdult(I)Z:0" in baseline, baseline)
        assertEquals(TaskOutcome.SUCCESS, check.task(":mutkt")?.outcome, check.output)
    }

    private companion object {
        val GATE_TEST =
            """
            package demo;
            import org.junit.jupiter.api.Assertions;
            import org.junit.jupiter.api.Test;
            public class GateTest {
                @Test void adult() { Assertions.assertTrue(new Gate().isAdult(30)); }
            }
            """.trimIndent()
    }
}
