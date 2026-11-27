package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.gradle.TestKitProject.Companion.assertSummaryLines
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Per-test coverage on a real build: which mutants run which tests, seen through the report's statuses. */
@Tag("e2e")
@Timeout(120)
class MutKtCoverageTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `coverage keeps weak and globally reached mutants scored as a full run and skips unreached ones`() {
        val project = TestKitProject(dir)
        project.setUp(
            "mutkt { operators.add('arithmetic') }",
            mapOf("demo/Shop.java" to SHOP, "demo/Labels.java" to LABELS),
            mapOf("demo/ShopTest.java" to SHOP_TEST),
        )
        val result = project.build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":mutkt")?.outcome, result.output)
        val summary = project.report.readText()

        assertSummaryLines(summary, "Mutants: 3", "KILLED       0", "SURVIVED     2", "NO_COVERAGE  1")
        // A full run survives both: nothing checks the product's size or the display name.
        assertTrue("demo.Shop.total(II)I" in summary, summary)
        assertTrue("demo.Labels.next(I)I" in summary, summary)
        assertTrue("demo.Shop.refund" !in summary, summary)
    }

    private companion object {
        val SHOP =
            """
            package demo;
            public class Shop {
                public int total(int price, int quantity) { return price * quantity; }
                public int refund(int price) { return price - 1; }
            }
            """.trimIndent()

        // Reached only while JUnit discovers the tests, outside any test class.
        val LABELS =
            """
            package demo;
            public class Labels {
                public static int next(int n) { return n + 1; }
            }
            """.trimIndent()

        // total(6, 3) is 2 when * becomes /, still positive; refund is never called.
        val SHOP_TEST =
            """
            package demo;
            import org.junit.jupiter.api.Assertions;
            import org.junit.jupiter.api.DisplayNameGeneration;
            import org.junit.jupiter.api.DisplayNameGenerator;
            import org.junit.jupiter.api.Test;
            @DisplayNameGeneration(ShopTest.Names.class)
            public class ShopTest {
                public static class Names extends DisplayNameGenerator.Standard {
                    @Override public String generateDisplayNameForClass(Class<?> type) {
                        return "shop " + Labels.next(1);
                    }
                }
                @Test void totals() { Assertions.assertTrue(new Shop().total(6, 3) > 0); }
            }
            """.trimIndent()
    }
}
