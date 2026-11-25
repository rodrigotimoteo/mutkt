package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.gradle.TestKitProject.Companion.calc
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** A class whose frames need a `compileOnly` type, absent from the test runtime classpath, is still mutated. */
@Tag("e2e")
@Timeout(120)
class MutKtUnresolvedTypeTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `a class whose frames need a compileOnly type is mutated`() {
        val project = TestKitProject(dir)
        project.setUp(sources = mapOf("demo/Calc.java" to calc("a + b"), "demo/Pick.java" to PICK))

        val result = project.build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":mutkt")?.outcome, result.output)
        assertTrue("Skipped" !in result.output, result.output)
        val report = project.report.readText()
        assertTrue("demo.Pick" in report, report)
    }

    private companion object {
        const val SUPPRESS = "com.github.rodrigotimoteo.mutkt.annotations.SuppressMutations"

        // The branches join a compileOnly type with String, so computing the frame needs that type's supertypes.
        val PICK =
            """
            package demo;
            import $SUPPRESS;
            public class Pick {
                public Object pick(boolean flag, SuppressMutations a, String b) { return flag ? a : b; }
                public int twice(int a) { return a * 2; }
            }
            """.trimIndent()
    }
}
