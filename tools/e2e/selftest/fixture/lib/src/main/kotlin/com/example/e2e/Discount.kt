package com.example.e2e

object Discount {
    fun price(total: Int): Int = if (total >= 100) total - 10 else total
}
