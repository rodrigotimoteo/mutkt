package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Another project's classes on the test classpath that call this project's main classes must call the mutants. */
@Tag("e2e")
@Tag("matrix")
@Timeout(180)
class MutKtOtherProjectFixturesTest {
    @TempDir
    lateinit var dir: File

    @OnGradleVersions
    fun `tests that go only through another project's test fixtures kill the mutants they call`(version: String) {
        val project = TestKitProject(dir, version)
        project.setUp(
            "dependencies { testImplementation testFixtures(project(':fixtures')) }",
            tests = mapOf("demo/CalcTest.java" to TEST),
        )
        project.write("settings.gradle", "rootProject.name = 'sample'\ninclude 'fixtures'\n")
        project.write("fixtures/build.gradle", FIXTURES_BUILD)
        project.write("fixtures/src/testFixtures/java/demo/fixtures/CalcFixture.java", FIXTURE)

        // The test classpath then holds the fixtures jar and sample.jar (original Calc); only the fixture calls Calc.
        val result = project.build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":mutkt")?.outcome, result.output)
        TestKitProject.assertSummaryLines(project.report.readText(), "KILLED       2", "SURVIVED     0")
    }

    private companion object {
        val FIXTURES_BUILD =
            """
            plugins {
                id 'java'
                id 'java-test-fixtures'
            }
            dependencies { testFixturesImplementation project(':') }
            """.trimIndent()

        val FIXTURE =
            """
            package demo.fixtures;
            import demo.Calc;
            public class CalcFixture {
                public static int addTwoAndThree() { return new Calc().add(2, 3); }
            }
            """.trimIndent()

        val TEST =
            """
            package demo;
            import demo.fixtures.CalcFixture;
            import org.junit.jupiter.api.Assertions;
            import org.junit.jupiter.api.Test;
            public class CalcTest {
                @Test void adds() { Assertions.assertEquals(5, CalcFixture.addTwoAndThree()); }
            }
            """.trimIndent()
    }
}
