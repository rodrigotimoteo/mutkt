package com.example.multiapp

import com.example.multishared.SharedLogic

/**
 * App-level entry point that wires the multi-flavor library
 * `SharedLogic` into a top-level decision class. The class is
 * deliberately small so the mutation test stays fast: every
 * branch has a direct test below.
 *
 * The :multi-shared dependency is resolved at AGP variant
 * resolution time. Without `missingDimensionStrategy("brand",
 * "production")` in the app's `defaultConfig`, Gradle cannot
 * pick a variant of `:multi-shared` (the library publishes
 * `productionDebug`, `stagingDebug`, `productionRelease`,
 * `stagingRelease`).
 */
class AppCoordinator {

    fun formatCount(value: Int): String =
        when {
            value < 0 -> "<negative>"
            value == 0 -> "0"
            else -> "value=$value"
        }

    fun summarize(input: String, count: Int): String {
        val safeInput = input.ifBlank { "<empty>" }
        val safeCount = if (count < 0) 0 else count
        return "$safeInput:$safeCount"
    }

    fun bridgeDescribe(value: Int): String = SharedLogic().describe(value)
}
