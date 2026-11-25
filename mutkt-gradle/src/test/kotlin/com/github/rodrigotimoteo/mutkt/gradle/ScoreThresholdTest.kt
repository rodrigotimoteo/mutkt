package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ScoreThresholdTest {
    @Test
    fun `passes above and at the threshold`() {
        assertNull(thresholdFailure(0.9, 80, "r"))
        assertNull(thresholdFailure(0.8, 80, "r"))
        assertNull(thresholdFailure(0.0, 0, "r"))
        // 29.0 / 100 and 57 / 100 are the doubles a percent-times-100 comparison would wrongly reject.
        assertNull(thresholdFailure(29.0 / 100, 29, "r"))
        assertNull(thresholdFailure(57.0 / 100, 57, "r"))
    }

    @Test
    fun `fails below the threshold with one line naming the report`() {
        assertEquals(
            "Mutation score 62.50% is below the threshold of 80.00% (report: build/reports/mutkt/mutkt.txt)",
            thresholdFailure(0.625, 80, "build/reports/mutkt/mutkt.txt"),
        )
        assertEquals(
            "Mutation score 79.96% is below the threshold of 80.00% (report: r)",
            thresholdFailure(0.7996, 80, "r"),
        )
    }

    @Test
    fun `is not applied without a threshold or a score`() {
        assertNull(thresholdFailure(0.1, null, "r"))
        assertNull(thresholdFailure(null, 100, "r"))
    }

    @Test
    fun `rejects a threshold outside 0 to 100`() {
        listOf(-1, 101).forEach {
            val failure = assertThrows<GradleException> { checkThresholdRange(it) }
            assertEquals("failBelowScore must be between 0 and 100 (percent), but is $it", failure.message)
        }
    }
}
