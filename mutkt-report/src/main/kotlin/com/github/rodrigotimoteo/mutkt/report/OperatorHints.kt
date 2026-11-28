package com.github.rodrigotimoteo.mutkt.report

import com.github.rodrigotimoteo.mutkt.domain.OperatorId

/** One hint per operator id. */
internal val operatorHints: Map<String, String> =
    mapOf(
        "negate-conditionals" to "no test checks what happens on both sides of the condition",
        "conditionals-boundary" to "no test checks the value exactly at the boundary",
        "arithmetic" to "no test asserts on the computed value",
        "null-returns" to "no test asserts on the returned object",
        "return-vals" to "no test asserts on the returned number",
        "empty-returns" to "no test asserts on the contents of the returned array or collection",
        "true-returns" to "no test expects this method to return false",
        "false-returns" to "no test expects this method to return true",
        "constructor-calls" to "no test uses the object built here",
        "increments" to "no test checks the value the counter reaches",
        "void-method-calls" to "no test checks the effect of the removed call",
        "non-void-method-calls" to "no test depends on the result of the replaced call",
        "sealed-when" to "no test takes this when branch and checks its result",
        "null-safety" to "no test covers the null case or the failing null check",
        "data-class-copy" to "no test uses the copied object",
        "coroutine" to "no test waits for the coroutine or checks its result",
        "when-switch" to "no test checks the result of this case or of the else branch",
        "remove-negation" to "no test checks the sign of the value",
        "stdlib-swap" to "no test checks which element or part of the collection the call returns",
        "extreme" to "tests run this method but no assertion depends on what it does",
    )

/** What a surviving mutant of [operator] usually says is missing from the tests; null for an operator without one. */
internal fun operatorHint(operator: OperatorId): String? = operatorHints[operator.value]
