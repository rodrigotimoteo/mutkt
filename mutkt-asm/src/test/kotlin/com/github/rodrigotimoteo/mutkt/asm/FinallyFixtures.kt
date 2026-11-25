package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.testing.compileKotlin

// Every finally body has its own constant, so a test can pick its site by the line that holds it.
// The probe functions reset the counter and swallow exceptions, so a test reads the counter after any path.
private val SOURCE =
    """
    var counter = 0
    class Res : java.io.Closeable {
        var closed = false
        override fun close() { closed = true }
    }

    fun tf(a: Int): Int {
        try {
            if (a < 0) throw IllegalStateException("negative")
            if (a > 5) return a + 1
            counter += 1
        } finally {
            counter = counter + 100
        }
        return a * 2
    }
    fun probe(a: Int): Int { counter = 0; try { tf(a) } catch (e: RuntimeException) { }; return counter }

    fun loopTf(n: Int): Int {
        var total = 0
        for (i in 0 until n) {
            try {
                if (i == 2) continue
                if (i == 5) break
                total += i
            } finally {
                counter = counter + 200
            }
        }
        return total
    }
    fun probeLoop(n: Int): Int { counter = 0; loopTf(n); return counter }

    fun tcf(s: String?): Int {
        try {
            return s!!.toInt() + 1
        } catch (e: NumberFormatException) {
            counter = counter - 1
            return -1
        } finally {
            counter = counter + 300
        }
    }
    fun probeTcf(s: String?): Int { counter = 0; try { tcf(s) } catch (e: RuntimeException) { }; return counter }

    fun nested(a: Int): Int {
        try {
            try {
                if (a < 0) throw IllegalStateException("negative")
                if (a > 5) return a
                counter += 1
            } finally {
                counter = counter + 400
            }
        } finally {
            counter = counter + 500
        }
        return 0
    }
    fun probeNested(a: Int): Int { counter = 0; try { nested(a) } catch (e: RuntimeException) { }; return counter }

    fun closing(r: Res): Int = r.use { it.closed.compareTo(false) + 1 }
    fun probeClosing(fail: Boolean): Boolean {
        val r = Res()
        try { r.use { if (fail) throw IllegalStateException("fail") } } catch (e: RuntimeException) { }
        return r.closed
    }

    fun sync(lock: Any): Int { synchronized(lock) { counter = counter + 600 }; return counter }
    fun same(a: Int, b: Int) = if (a > b) a + 1 else b + 1

    fun finIf(a: Int): Int {
        try { if (a > 5) return 1 } finally { if (a > 3) counter = counter + 700 }
        return 2
    }
    fun probeIf(a: Int): Int { counter = 0; finIf(a); return counter }

    fun finRet(a: Int): Int {
        try { if (a > 5) return 1 } finally { if (a > 3) return counter + 800 }
        return 2
    }

    fun finLambda(a: Int): Int {
        try { if (a > 5) return 1 } finally { listOf(1, 2).forEach { counter = counter + it + 900 } }
        return 2
    }
    fun probeLambda(a: Int): Int { counter = 0; finLambda(a); return counter }

    fun finLocal(a: Int): Int {
        try { if (a > 5) return 1 } finally { val x = a + 1000; counter = counter + x }
        return 2
    }
    fun probeLocal(a: Int): Int { counter = 0; finLocal(a); return counter }

    fun reads(a: Int, b: Int): Int {
        try { if (a > 5) return 1 } finally { counter = counter + a }
        return b
    }

    fun inFinally(a: Int): Int {
        try {
            if (a > 5) return 1
        } finally {
            try { counter = counter + 1200 } finally { counter = counter + 1300 }
        }
        return 2
    }
    fun probeInFinally(a: Int): Int { counter = 0; inFinally(a); return counter }

    fun lookalike(a: Int): Int {
        try { counter = counter + 1100 } finally { counter = counter + 1100 }
        return a
    }
    """.trimIndent()

internal val finallyClasses: Map<String, ByteArray> by lazy { compileKotlin(SOURCE) }

/** The 1-based source line of the first line of the fixture holding [text]. */
internal fun lineOf(text: String): Int = SOURCE.lines().indexOfFirst { text in it } + 1
