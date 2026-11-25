package com.github.rodrigotimoteo.mutkt.report

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SchemaValidatorTest {
    private val validator = SchemaValidator(schemaText())

    @Test
    fun `a minimal report passes`() {
        assertEquals(
            emptyList<String>(),
            validator.validate("""{"schemaVersion":"2","thresholds":{"high":80,"low":60},"files":{}}"""),
        )
    }

    @Test
    fun `missing, mistyped and out of range values are reported`() {
        val errors =
            validator.validate(
                """{"schemaVersion":"3","thresholds":{"high":80},"files":{"a":{"language":1,"source":"",""" +
                    """"mutants":[{"id":"x","mutatorName":"m","status":"Nope",""" +
                    """"location":{"start":{"line":0,"column":1},"end":{"line":1,"column":1}}}]}}}""",
            )

        assertEquals(
            setOf(
                "$.schemaVersion does not match ^([1-2])(\\.(([1-9]\\d*)|0)){0,2}$",
                "$.thresholds lacks low",
                "$.files.a.language is not string",
                "$.files.a.mutants[0].status is not one of " +
                    """["Killed","Survived","NoCoverage","CompileError",""" +
                    """"RuntimeError","Timeout","Ignored","Pending"]""",
                "$.files.a.mutants[0].location.start.line is below 1",
            ),
            errors.toSet(),
        )
    }
}

internal fun schemaText(): String =
    SchemaValidator::class.java.getResource("/mutation-testing-report-schema.json")!!.readText()
