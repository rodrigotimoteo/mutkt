package com.example.e2e

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiscountTest {
    @Test
    fun `small totals pay full price`() = assertEquals(50, Discount.price(50))

    // No total of exactly 100, so the boundary mutant survives.
    @Test
    fun `large totals get ten off`() = assertEquals(190, Discount.price(200))
}
