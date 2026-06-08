package com.github.rodrigotimoteo.mutation.sample

/**
 * Sealed class representing computation results.
 * Tests sealed class when-expression mutation.
 */
sealed class ComputationResult {
    data class Success(val value: Double) : ComputationResult()
    data class Error(val message: String) : ComputationResult()
    object Loading : ComputationResult()
}

/**
 * Processes computation results.
 */
fun processResult(result: ComputationResult): String {
    return when (result) {
        is ComputationResult.Success -> "Value: ${result.value}"
        is ComputationResult.Error -> "Error: ${result.message}"
        is ComputationResult.Loading -> "Loading..."
    }
}

fun resultValue(result: ComputationResult): Double? {
    return when (result) {
        is ComputationResult.Success -> result.value
        is ComputationResult.Error -> null
        is ComputationResult.Loading -> null
    }
}
