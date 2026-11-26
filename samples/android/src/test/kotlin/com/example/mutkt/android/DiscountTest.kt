package com.example.mutkt.android

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscountTest {
    @Test
    fun discountsLargeTotals() {
        assertEquals(140, Discount().price(150))
    }

    @Test
    fun keepsSmallTotals() {
        assertEquals(50, Discount().price(50))
    }
}
