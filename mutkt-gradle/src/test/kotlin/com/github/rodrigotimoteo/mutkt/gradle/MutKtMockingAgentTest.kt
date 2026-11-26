package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * A MockK static mock needs the inline agent. With dynamic agent loading turned off, MockK cannot self-attach, so the
 * test only passes when the `test` task's `-javaagent` reaches the mutants' JVMs too.
 */
@Tag("e2e")
@Tag("matrix")
@Timeout(180)
class MutKtMockingAgentTest {
    @TempDir
    lateinit var dir: File

    @OnGradleVersions
    fun `a test that needs its explicit mocking agent passes in the test task and in the mutant runs`(version: String) {
        val project = TestKitProject(dir, version)
        val agent = project.jars(Regex("^byte-buddy-agent"))
        project.setUp(
            """
            dependencies { testImplementation files(${project.jars(MOCKK_JARS)}) }
            tasks.test { jvmArgs '-XX:-EnableDynamicAgentLoading', '-javaagent:' + $agent }
            """.trimIndent(),
            sources = mapOf("demo/Prices.java" to PRICES),
            tests = mapOf("demo/PricesTest.java" to TEST),
        )

        val plain = project.build("test")
        assertEquals(TaskOutcome.SUCCESS, plain.task(":test")?.outcome, plain.output)
        assertTrue(project.ranTests("demo.PricesTest"), plain.output)

        val result = project.build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":mutkt")?.outcome, result.output)
        val summary = project.report.readText()
        // The multiplication and the return of total() are killed; base() is mocked, so its mutant survives.
        listOf("Mutants: 3", "KILLED       2", "SURVIVED     1").forEach { line ->
            assertTrue(summary.lines().any { it.trim() == line }, "Missing line '$line' in:\n$summary")
        }
    }

    private companion object {
        val MOCKK_JARS = Regex("^(mockk|byte-buddy|objenesis|kotlin-reflect|kotlinx-coroutines-core)")

        val PRICES =
            """
            package demo;
            public class Prices {
                public static int base() { return 10; }
                public int total(int qty) { return base() * qty; }
            }
            """.trimIndent()

        // The string form: MockK resolves the name through its own class loader, which must see the project's classes.
        val TEST =
            """
            package demo;
            import io.mockk.MockKKt;
            import org.junit.jupiter.api.Assertions;
            import org.junit.jupiter.api.Test;
            public class PricesTest {
                @Test void totalUsesTheMockedBase() {
                    MockKKt.mockkStatic("demo.Prices");
                    try {
                        MockKKt.every(scope -> Prices.base()).returns(3);
                        Assertions.assertEquals(6, new Prices().total(2));
                    } finally {
                        MockKKt.unmockkStatic("demo.Prices");
                    }
                }
            }
            """.trimIndent()
    }
}
