package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Classes of other source sets on the test classpath (fixtures, shared test code) must call the mutants. */
@Tag("e2e")
@Tag("matrix")
@Timeout(120)
class MutKtSupportClassesTest {
    @TempDir
    lateinit var dir: File

    @OnGradleVersions
    fun `tests that go through a java-test-fixtures class kill the mutants it calls`(version: String) {
        val project = TestKitProject(dir, version)
        project.setUp(tests = mapOf("demo/CalcTest.java" to TEST))
        val build = dir.resolve("build.gradle")
        build.writeText(build.readText().replace("id 'java'", "id 'java'\n    id 'java-test-fixtures'"))
        project.write("src/testFixtures/java/demo/CalcFixture.java", FIXTURE)

        assertKillsBoth(project)
    }

    @OnGradleVersions
    fun `tests that go through a custom source set on the test classpath kill the mutants it calls`(version: String) {
        val project = TestKitProject(dir, version)
        project.setUp(SHARED_SOURCE_SET, tests = mapOf("demo/CalcTest.java" to TEST))
        project.write("src/testCommon/java/demo/CalcFixture.java", FIXTURE)

        assertKillsBoth(project)
    }

    private fun assertKillsBoth(project: TestKitProject) {
        val result = project.build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":mutkt")?.outcome, result.output)
        val summary = project.report.readText()
        assertTrue(summary.lines().any { it.trim() == "KILLED       2" }, summary)
    }

    private companion object {
        val SHARED_SOURCE_SET =
            """
            sourceSets {
                testCommon { compileClasspath += sourceSets.main.output }
                test {
                    compileClasspath += testCommon.output
                    runtimeClasspath += testCommon.output
                }
            }
            """.trimIndent()

        val FIXTURE =
            """
            package demo;
            public class CalcFixture {
                public static int addTwoAndThree() { return new Calc().add(2, 3); }
            }
            """.trimIndent()

        val TEST =
            """
            package demo;
            import org.junit.jupiter.api.Assertions;
            import org.junit.jupiter.api.Test;
            public class CalcTest {
                @Test void adds() { Assertions.assertEquals(5, CalcFixture.addTwoAndThree()); }
            }
            """.trimIndent()
    }
}
