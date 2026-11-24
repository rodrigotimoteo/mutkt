package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.testing.compileKotlin

// No string templates: they would need escaping inside this raw string.
// Lambdas compile to invokedynamic plus synthetic methods, which isMutable skips; their bodies are covered
// through the inlined collection functions (map, filter, fold) used below.
private val SOURCE =
    """
    fun add(a: Int, b: Int) = a + b
    fun add(a: Int, b: Int, c: Int) = a + b + c
    fun add(a: Long, b: Long) = a + b
    fun scale(a: Double, b: Double) = a * b - a / (b + 1.0) + a % 3.0

    fun classify(a: Int): String =
        when {
            a > 10 -> "big"
            a == 5 || a < 0 -> "odd"
            a in 6..9 -> "mid"
            else -> "small"
        }

    fun describe(a: Any?): Int =
        when (a) {
            is Int -> a + 1
            is String -> a.length
            null -> -1
            else -> 0
        }

    fun loops(n: Int): Int {
        var total = 0
        for (i in 0 until n) total += if (i % 2 == 0) i else -i
        var j = n
        while (j > 0) { total += j; j-- }
        do { total++ } while (total < 3)
        return total
    }

    fun guarded(s: String): Int =
        try {
            s.toInt() + 1
        } catch (e: NumberFormatException) {
            -1
        } finally {
            counter++
        }

    var counter = 0

    fun lambdas(xs: List<Int>): Int {
        val inc: (Int) -> Int = { it + 1 }
        val offset = xs.size
        return xs.map(inc).filter { it > 1 }.fold(offset) { acc, x -> acc + x }
    }

    fun text(a: String, b: String?): String {
        val joined = a + "-" + (b ?: "none")
        return joined.uppercase().take(3) + joined.length + (b?.length ?: 0) + b?.trim().orEmpty()
    }

    fun bits(a: Int, b: Int) = (a and b) or (a xor b) shl 1 shr 2 ushr 1

    data class Point(val x: Int, val y: Int) {
        fun plus(o: Point) = Point(x + o.x, y + o.y)
        fun norm() = x * x + y * y
    }

    object Registry {
        private val names = mutableListOf<String>()
        fun register(n: String): Int {
            names.add(n)
            return names.size - 1
        }
    }

    open class Animal { open fun legs() = 4 }
    class Dog : Animal() { override fun legs() = super.legs() - 1 }
    class Cat : Animal()

    class Counter(private var start: Int = 0) {
        fun next(step: Int = 1): Int {
            start += step
            return start
        }

        companion object {
            const val LIMIT = 100
            fun capped(a: Int) = if (a > LIMIT) LIMIT else a
        }
    }

    sealed class Shape
    class Circle(val r: Int) : Shape()
    class Square(val s: Int) : Shape()
    object Empty : Shape()

    fun area(shape: Shape): Int =
        when (shape) {
            is Circle -> 3 * shape.r * shape.r
            is Square -> shape.s * shape.s
            Empty -> 0
        }

    enum class Color { RED, GREEN, BLUE }

    // A when over an enum compiles to a WhenMappings table and a tableswitch.
    fun hex(c: Color): Int =
        when (c) {
            Color.RED -> 255 + 1
            Color.GREEN -> 128 + 2
            Color.BLUE -> 64 + 3
        }

    // A when on String compiles to a hashCode lookupswitch.
    fun code(s: String): Int =
        when (s) {
            "one" -> 1 + 0
            "two" -> 1 + 1
            "three" -> 1 + 2
            else -> -1
        }

    // Dense constants compile to a TABLESWITCH.
    fun month(n: Int): Int =
        when (n) {
            1 -> n + 30
            2 -> n + 27
            3 -> n + 28
            4 -> n + 26
            else -> n
        }

    fun pick(flag: Boolean, a: Int): Int {
        val pet: Animal = if (flag) Dog() else Cat()
        return pet.legs() + a
    }
    """.trimIndent()

/** Class bytes by binary name for a representative Kotlin program, compiled once for all operators. */
internal val corpus: Map<String, ByteArray> by lazy { compileKotlin(SOURCE) }
