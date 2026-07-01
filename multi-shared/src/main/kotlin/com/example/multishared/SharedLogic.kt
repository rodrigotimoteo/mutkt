package com.example.multishared

/**
 * Pure-Kotlin logic class published from a multi-flavor Android
 * library. The mutation test scans this class for surviving
 * mutants; the tests below cover every branch so the score stays
 * high.
 *
 * Lives in :multi-shared, which exposes two product flavors
 * (`production`, `staging`) on the `brand` dimension. The multi-flavor
 * scenario: an app depending on this library without
 * `missingDimensionStrategy("brand", "production")` cannot
 * resolve the library's `*RuntimeElements` variants and fails.
 */
class SharedLogic {
    fun describe(value: Int): String =
        when {
            value < 0 -> "negative"
            value == 0 -> "zero"
            value == 1 -> "one"
            value < 10 -> "small"
            else -> "large"
        }

    fun sumPositive(values: List<Int>): Int =
        values.filter { it > 0 }.sum()

    fun isPalindrome(text: String): Boolean {
        if (text.length <= 1) return true
        val normalized = text.lowercase()
        return normalized == normalized.reversed()
    }

    fun normalize(input: String?): String =
        if (input.isNullOrBlank()) "<empty>" else input.trim().uppercase()
}
