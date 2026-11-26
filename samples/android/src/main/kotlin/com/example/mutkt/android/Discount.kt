package com.example.mutkt.android

/** Takes 10 off totals of 100 or more. */
class Discount {
    fun price(total: Int): Int = if (total >= 100) total - 10 else total
}
