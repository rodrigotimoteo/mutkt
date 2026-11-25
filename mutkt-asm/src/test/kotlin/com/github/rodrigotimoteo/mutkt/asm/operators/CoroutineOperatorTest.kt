package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Hierarchy
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.ThrowingSupplier
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Duration

// Real kotlinx.coroutines output: the library reaches the fixture compiler through the test classpath
// (a transitive of the compiler test dependency), so the fixtures are plain Kotlin using runBlocking and friends.
private val DECLARATIONS =
    listOf(
        "var sink = 0",
        "suspend fun leaf(n: Int): Int = n + 1",
        "suspend fun twice(n: Int): Int = leaf(n) + leaf(n + 1)",
        "suspend fun tail(n: Int): Int = leaf(n)",
        "suspend fun scaled(n: Int, k: Int = 2): Int = n * k",
        "suspend fun callScaled(n: Int): Int = scaled(n)",
        "fun runScaled(n: Int): Int = runBlocking { callScaled(n) + 0 }",
        "suspend fun unitStep(n: Int) { sink = n }",
        "suspend fun withUnit(n: Int): Int { unitStep(n); return sink + 1 }",
        "suspend fun wideLeaf(a: Long, b: Double): Long = a + b.toLong()",
        "suspend fun callWide(a: Long): Long = wideLeaf(a, 1.5) + 1",
        "interface Source { suspend fun next(): Int }",
        "suspend fun drain(s: Source): Int = s.next() + s.next()",
        """
        class Worker {
            suspend fun work(n: Int): Int = leaf(n) * 2
            private suspend fun hidden(n: Int): Int = n + 1
            suspend fun viaHidden(n: Int): Int = hidden(n) + hidden(n + 1)
        }
        """,
        "fun runTwice(n: Int): Int = runBlocking { twice(n) }",
        "fun runTail(n: Int): Int = runBlocking { tail(n) }",
        "fun runWithUnit(n: Int): Int = runBlocking { withUnit(n) }",
        "fun runWide(a: Long): Long = runBlocking { callWide(a) }",
        "fun runWorker(n: Int): Int = runBlocking { Worker().work(n) + Worker().viaHidden(n) }",
        "fun runDrain(): Int = runBlocking { drain(object : Source { var i = 0; override suspend fun next() = ++i }) }",
        """
        fun builders(n: Int): Int {
            sink = 0
            runBlocking {
                val job = launch { sink += n }
                job.join()
                val deferred = async { n + 1 }
                sink += deferred.await()
                sink += withContext(Dispatchers.Default) { n * 2 }
                delay(1)
            }
            return sink
        }
        """,
        "fun unusedLaunch(n: Int): Int { sink = 0; runBlocking { launch { sink = n } }; return sink }",
        "fun global(n: Int) { GlobalScope.launch { sink = n } }",
        "fun cancelled(): Boolean { val job = Job(); job.cancel(); return job.isCancelled }",
        """
        fun stillActive(): Int {
            val job = Job()
            job.cancel()
            try { job.ensureActive() } catch (e: CancellationException) { return 1 }
            return 0
        }
        """,
        "fun plain(n: Int): Int = n + 1",
    )

private fun source(declarations: List<String>) =
    "import kotlinx.coroutines.*\n" + declarations.joinToString("\n") { it.trimIndent() }

private val CLASSES = compileKotlin(source(DECLARATIONS), inheritClassPath = true)
private val REVERSED = compileKotlin(source(DECLARATIONS.reversed()), inheritClassPath = true)

private const val FILE = "SourceKt"

class CoroutineOperatorTest {
    private val noLookup: Hierarchy = { null }

    private fun sites(
        method: String,
        className: String = FILE,
        from: Map<String, ByteArray> = CLASSES,
    ) = discover(from.getValue(className), CoroutineOperator).filter { it.methodName == method }

    private fun mutant(
        method: String,
        ordinal: Int,
        className: String = FILE,
    ): Map<String, ByteArray> {
        val found = sites(method, className).single { it.ordinal == ordinal }
        return CLASSES + (className to mutate(CLASSES.getValue(className), CoroutineOperator, found, noLookup))
    }

    private val loader =
        object : ClassLoader(CoroutineOperatorTest::class.java.classLoader) {
            override fun findClass(name: String): Class<*> =
                CLASSES[name]?.let { defineClass(name, it, 0, it.size) } ?: throw ClassNotFoundException(name)
        }

    private fun verify(bytes: ByteArray) =
        StringWriter().also { CheckClassAdapter.verify(ClassReader(bytes), loader, false, PrintWriter(it)) }.toString()

    // Loading runs the JVM verifier on the mutant; a state machine broken by the mutation would not link.
    private fun outcome(
        classes: Map<String, ByteArray>,
        method: String,
        vararg args: Any?,
    ): Any? =
        assertTimeoutPreemptively(
            Duration.ofSeconds(20),
            ThrowingSupplier {
                runCatching {
                    loadAndInvoke(
                        classes,
                        FILE,
                        method,
                        *args,
                    )
                }.fold({ it }, { it.javaClass })
            },
        )

    private fun descriptions(
        method: String,
        className: String = FILE,
    ) = sites(method, className).map { it.description.substringAfter(": ") }

    @Test
    fun `suspend calls are sites of the suspend function in order`() {
        assertEquals(listOf("SourceKt.leaf", "SourceKt.leaf"), descriptions("twice"))
        assertEquals(listOf(0, 1), sites("twice").map { it.ordinal })
        assertEquals(listOf("SourceKt.wideLeaf"), descriptions("callWide"))
    }

    @Test
    fun `a tail call is a site and its mutant returns null`() {
        assertEquals(listOf("SourceKt.leaf"), descriptions("tail"))
        assertEquals(6, outcome(CLASSES, "runTail", 5))
        assertEquals(NullPointerException::class.java, outcome(mutant("tail", 0), "runTail", 5))
    }

    @Test
    fun `each suspend call of a state machine fails its own way`() {
        assertEquals(5, outcome(CLASSES, "runTwice", 1))
        listOf(0, 1).forEach {
            assertEquals(NullPointerException::class.java, outcome(mutant("twice", it), "runTwice", 1))
            assertEquals("", verify(mutant("twice", it).getValue(FILE)))
        }
    }

    @Test
    fun `a suspend call with default arguments goes through the default bridge and is a site`() {
        assertEquals(listOf("SourceKt.scaled\$default"), descriptions("callScaled"))
        assertEquals(6, outcome(CLASSES, "runScaled", 3))
        assertEquals(NullPointerException::class.java, outcome(mutant("callScaled", 0), "runScaled", 3))
        assertEquals("", verify(mutant("callScaled", 0).getValue(FILE)))
    }

    @Test
    fun `a suspend lambda has sites only in invokeSuspend, never in its bridges`() {
        val lambda = "SourceKt\$builders\$1"

        assertEquals(setOf("invokeSuspend"), sites("invokeSuspend", lambda).map { it.methodName }.toSet())
        assertEquals(6, discover(CLASSES.getValue(lambda), CoroutineOperator).size)
    }

    @Test
    fun `a unit suspend call is skipped without breaking the state machine`() {
        assertEquals(4, outcome(CLASSES, "runWithUnit", 3))
        assertEquals(1, outcome(mutant("withUnit", 0), "runWithUnit", 3))
    }

    @Test
    fun `long and double arguments are popped as two slots`() {
        assertEquals(4L, outcome(CLASSES, "runWide", 2L))
        assertEquals(NullPointerException::class.java, outcome(mutant("callWide", 0), "runWide", 2L))
        assertEquals("", verify(mutant("callWide", 0).getValue(FILE)))
    }

    @Test
    fun `interface, virtual and private instance suspend calls are sites`() {
        assertEquals(listOf("Source.next", "Source.next"), descriptions("drain"))
        assertEquals(listOf("Worker.hidden", "Worker.hidden"), descriptions("viaHidden", "Worker"))
        assertEquals(3, outcome(CLASSES, "runDrain"))
        (0..1).forEach { assertEquals(NullPointerException::class.java, outcome(mutant("drain", it), "runDrain")) }
    }

    @Test
    fun `builder calls inside a suspend lambda are sites in order`() {
        val lambda = "SourceKt\$builders\$1"

        assertEquals(
            listOf("launch\$default", "join", "async\$default", "await", "withContext", "delay"),
            descriptions("invokeSuspend", lambda).map { it.substringAfter('.') },
        )
        assertEquals(listOf("BuildersKt.runBlocking\$default"), descriptions("builders"))
    }

    @Test
    fun `every builder mutation of a running coroutine changes the outcome and links`() {
        val lambda = "SourceKt\$builders\$1"
        val expected = outcome(CLASSES, "builders", 2)

        assertEquals(9, expected)
        sites("invokeSuspend", lambda).forEach {
            assertEquals("", verify(mutant("invokeSuspend", it.ordinal, lambda).getValue(lambda)))
            val changed = outcome(mutant("invokeSuspend", it.ordinal, lambda), "builders", 2) != expected
            // Skipping a one millisecond delay is the equivalent mutant.
            assertEquals("delay" !in it.description, changed, it.description)
        }
        assertTrue(outcome(mutant("builders", 0), "builders", 2) != expected)
    }

    @Test
    fun `a launch whose job is discarded disappears`() {
        assertEquals(4, outcome(CLASSES, "unusedLaunch", 4))
        assertEquals(0, outcome(mutant("invokeSuspend", 0, "SourceKt\$unusedLaunch\$1"), "unusedLaunch", 4))
    }

    @Test
    fun `global scope builders and job helpers are sites`() {
        assertEquals(listOf("BuildersKt.launch\$default"), descriptions("global"))
        assertEquals(listOf("JobKt.Job\$default"), descriptions("cancelled"))
        assertEquals(true, outcome(CLASSES, "cancelled"))
        assertEquals(NullPointerException::class.java, outcome(mutant("cancelled", 0), "cancelled"))
    }

    @Test
    fun `a void job helper is removed`() {
        assertEquals(listOf("JobKt.Job\$default", "JobKt.ensureActive"), descriptions("stillActive"))
        assertEquals(1, outcome(CLASSES, "stillActive"))
        assertEquals(0, outcome(mutant("stillActive", 1), "stillActive"))
    }

    @Test
    fun `the suspension marker, the continuation re-entry and compiler plumbing are not sites`() {
        val all = CLASSES.flatMap { (name, _) -> sites("invokeSuspend", name) + sites("twice", name) }
        assertTrue(all.none { "COROUTINE_SUSPENDED" in it.description || "throwOnFailure" in it.description })
        // The continuation class of a suspend function resumes by calling the function again.
        assertEquals(emptyList<Any>(), sites("invokeSuspend", "SourceKt\$twice\$1"))
        assertEquals(emptyList<Any>(), sites("invokeSuspend", "SourceKt\$withUnit\$1"))
        val plumbing = CLASSES.values.flatMap { discover(it, CoroutineOperator) }.map { it.description }
        assertTrue(plumbing.none { "Boxing" in it || ".create" in it || "<init>" in it }, plumbing.toString())
    }

    @Test
    fun `non-coroutine code has no site`() {
        assertEquals(emptyList<Any>(), sites("plain"))
    }

    @Test
    fun `every mutant of every site links and finishes every entry point`() {
        val entries =
            listOf(
                "runTwice" to arrayOf<Any?>(1),
                "runTail" to arrayOf(5),
                "runWithUnit" to arrayOf(3),
                "runWide" to arrayOf(2L),
                "runScaled" to arrayOf(3),
                "runWorker" to arrayOf(1),
                "runDrain" to arrayOf(),
                "builders" to arrayOf(2),
                "unusedLaunch" to arrayOf(4),
                "cancelled" to arrayOf(),
                "stillActive" to arrayOf(),
            )
        val all =
            CLASSES.keys.flatMap { name ->
                discover(CLASSES.getValue(name), CoroutineOperator).map { name to it }
            }

        assertEquals(39, all.size)
        all.forEach { (name, site) ->
            val bytes = mutate(CLASSES.getValue(name), CoroutineOperator, site, noLookup)
            assertEquals("", verify(bytes), "$site")
            entries.forEach { (method, args) -> outcome(CLASSES + (name to bytes), method, *args) }
        }
    }

    @Test
    fun `declaration order does not change the sites`() {
        fun summary(from: Map<String, ByteArray>) =
            from.keys.sorted().associateWith { name ->
                discover(from.getValue(name), CoroutineOperator)
                    .map {
                        "${it.methodName}${it.descriptor}#${it.ordinal} ${it.description}"
                    }.sorted()
            }

        assertEquals(summary(CLASSES), summary(REVERSED))
        assertEquals(39, summary(REVERSED).values.sumOf { it.size })
    }
}
