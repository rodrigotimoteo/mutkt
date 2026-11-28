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

    // Copy shapes: all arguments, default arguments, wide arguments, a chain, a call from another class.
    data class Wide2(val a: Long, val b: Double, val c: String)
    class Mover { fun move(p: Point) = p.copy(x = 1).x }
    fun copies(a: Int, w: Long): Int {
        val p = Point(a, a).copy(7, 8).copy(y = 3)
        return p.x + Wide2(w, 1.5, "s").copy(b = 2.5).copy(w, 3.5, "t").a.toInt()
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

    sealed interface Op
    class Add(val a: Int) : Op
    class Neg(val a: Int) : Op

    // Type tests on one subject: a sealed interface, multi-condition branches, an object branch and an else.
    fun sign(op: Op): Int = when (op) { is Add -> op.a; is Neg -> -op.a }
    fun kind(shape: Shape): Int =
        when (shape) {
            is Circle, is Square -> 1
            Empty -> 2
        }
    fun fallback(shape: Shape): Int = when (shape) { is Circle -> 1; is Square -> 2; else -> 3 }

    // Enum whens: a statement with else, a nullable subject (case -1 is null), shared bodies, a sparse ordinal switch.
    fun note(c: Color): String {
        var s = "-"
        when (c) { Color.RED -> s = "r"; Color.GREEN -> s = "g"; else -> {} }
        return s
    }
    fun nullable(c: Color?): Int = when (c) { Color.RED -> 1; null -> 0; else -> 5 }
    fun grouped(c: Color): Int = when (c) { Color.RED, Color.GREEN -> 1; Color.BLUE -> 2 }
    fun sparse(c: Color): Int = when (c.ordinal) { 0 -> 10; 100 -> 20; else -> 0 }

    // A when on String compiles to a hashCode lookupswitch.
    fun code(s: String): Int =
        when (s) {
            "one" -> 1 + 0
            "two" -> 1 + 1
            "three" -> 1 + 2
            else -> -1
        }

    fun names(n: Int): List<String> = if (n > 0) listOf("a") else emptyList()
    fun lookup(n: Int): Map<String, Int> = mapOf("a" to n)
    fun uniques(n: Int): Set<Int> = setOf(n)
    fun all(n: Int): Collection<Int> = listOf(n)
    fun buffer(n: Int): IntArray = IntArray(n)
    fun grid(n: Int): Array<IntArray> = arrayOf(IntArray(n))
    fun labels(n: Int): Array<String> = arrayOf("a")

    // Dense constants compile to a TABLESWITCH.
    fun month(n: Int): Int =
        when (n) {
            1 -> n + 30
            2 -> n + 27
            3 -> n + 28
            4 -> n + 26
            else -> n
        }

    // Constructor shapes: wide args, nested construction, a branch inside the args, default-arg constructor.
    class Wide(val a: Long, val b: Double, val c: Int)
    class Wrap(val inner: Wide?, val tag: String = "t")
    fun wrapped(a: Long, d: Double) = Wrap(Wide(a, d, if (a > 0) 1 else 2))
    fun tagged(a: Long) = Wrap(null).tag + Wrap(Wide(a, 1.0, 0), "x").tag.length

    fun flags(a: Int, s: String): Boolean {
        val r = a > 1
        if (s.isEmpty()) return false
        if (a == 0) return true
        return r
    }
    fun isBig(a: Int) = a > 10
    fun wideFlag(a: Long, d: Double) = a > d
    class Switch(val on: Boolean) { fun isOn(): Boolean = on }
    class CallSink {
        var hits = 0
        fun bumpWide(a: Long, b: Double, c: Int) { hits += a.toInt() + b.toInt() + c }
        fun total(a: Long, b: Double): Long = a + hits + b.toLong()
        fun codes(): IntArray = intArrayOf(hits)
    }
    fun tick() { counter++ }
    fun callShapes(): Long {
        val s = CallSink()
        s.bumpWide(10L, 2.5, 3)
        s.codes()
        try { tick() } catch (e: Exception) { return -1 }
        return s.total(5L, 1.5)
    }

    // Compiler-generated noise: an inline body copied into callers, a regenerated object, a String when.
    inline fun inlineTwice(a: Int, f: (Int) -> Int): Int = f(a) + f(a + 1)
    fun inlined(n: Int): Int = inlineTwice(n + 1) { it + 10 }
    interface InlineCb { fun call(): Int }
    inline fun inlineMake(crossinline f: () -> Int) = object : InlineCb { override fun call() = f() + 1 }
    fun inlinedObject(n: Int): Int = inlineMake { n + 2 }.call()
    fun inlinedStdlib(l: List<String>): List<String> = l.sortedBy { it.length + 1 }.map { it + "!" }
    fun byName(s: String): Int = when (s) { "a" -> 1 + 0; "b" -> 2 + 0; "c" -> 3 + 0; else -> 4 }
    // Suspend calls: a state machine, a tail call, wide arguments, an instance receiver.
    suspend fun leaf(a: Int): Int = a + 1
    suspend fun twice(a: Int): Int = leaf(a) + leaf(a + 1)
    suspend fun tailCall(a: Int): Int = leaf(a)
    suspend fun wideLeaf(a: Long, b: Double): Long = a + b.toLong()
    suspend fun callWide(a: Long): Long = wideLeaf(a, 1.5) + 1
    class Worker { suspend fun work(a: Int): Int = leaf(a) * 2 }
    suspend fun delegate(w: Worker, a: Int): Int = w.work(a) + w.work(a + 1)
    // Null-handling shapes: lateinit read, not-null assertion, platform-type result, parameter check.
    class Late { lateinit var name: String }
    fun lateName(l: Late): String = l.name
    fun forced(s: String?): Int = s!!.length + 1
    fun platformValue(): String = System.getProperty("mutkt.absent.property")
    // String shapes: a message literal, a builder with wide appends, a key literal that is not a site.
    fun message(n: Int): String = if (n > 0) "positive" else "other"
    fun built(a: Long, d: Double): String = buildString { append("v="); append(a); append(';'); append(d) }
    fun keyed(): Class<*> = Class.forName("java.lang.String")

    fun pick(flag: Boolean, a: Int): Int {
        val pet: Animal = if (flag) Dog() else Cat()
        return pet.legs() + a
    }
    """.trimIndent()

/** Class bytes by binary name for a representative Kotlin program, compiled once for all operators. */
internal val corpus: Map<String, ByteArray> by lazy { compileKotlin(SOURCE) }
