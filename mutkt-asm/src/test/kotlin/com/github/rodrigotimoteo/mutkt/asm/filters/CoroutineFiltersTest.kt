package com.github.rodrigotimoteo.mutkt.asm.filters

import com.github.rodrigotimoteo.mutkt.asm.Found
import com.github.rodrigotimoteo.mutkt.asm.Operators
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.asm.operators.NegateConditionalsOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.StringsOperator
import com.github.rodrigotimoteo.mutkt.asm.parse
import com.github.rodrigotimoteo.mutkt.asm.toBytes
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.objectweb.asm.tree.LineNumberNode

// SuspendMarkerFilter, ContinuationFilter and ResumeMessageFilter: the coroutine plumbing, on one compilation.
class CoroutineFiltersTest {
    private val classes =
        compileKotlin(
            """
            import kotlinx.coroutines.launch
            import kotlinx.coroutines.runBlocking
            import kotlinx.coroutines.yield

            var sink = 0
            suspend fun leaf(n: Int): Int { yield(); return n + 1 }
            suspend fun twice(n: Int): Int { val a = leaf(n); return if (a > 3) a + leaf(a) else a }
            suspend fun store(n: Int) { leaf(n); sink = n }
            suspend fun tailUnit(n: Int) { store(n) }
            fun runTwice(n: Int): Int = runBlocking { twice(n) }
            fun launched(n: Int): Int = runBlocking { launch { if (n > 2) sink = leaf(n) * 2 }.join(); sink }
            fun unitValue(): Any? = Unit
            suspend fun anyUnit(n: Int): Any { leaf(n); return Unit }
            suspend fun same(a: Any, b: Any): Boolean { leaf(1); return a === b }
            class Owner {
                class State(@JvmField var label: Int)
                suspend fun work(s: State): Int {
                    leaf(1)
                    return if (s.label and Int.MIN_VALUE != 0) s.label - Int.MIN_VALUE else s.label
                }
            }
            class Counter(var label: Int) {
                fun masked(): Int = if (label and Int.MIN_VALUE != 0) label - Int.MIN_VALUE else label
            }
            """.trimIndent(),
            inheritClassPath = true,
        )
    private val file = classes.getValue("SourceKt")

    private fun markers(
        className: String,
        method: String,
    ) = SuspendMarkerFilter.noiseIn(classes, className, method)

    private fun continuation(
        className: String,
        method: String,
    ) = ContinuationFilter.noiseIn(classes, className, method)

    private fun defaultSites(
        className: String,
        method: String,
    ) = Operators.defaults
        .flatMap { discover(classes.getValue(className), it) }
        .filter { it.methodName == method }
        .map { it.description }

    @Test
    fun `the state machine of a suspend function is noise, its own code is not`() {
        // The prologue (instanceof, label test, label clear), then a marker check and return per suspension point.
        assertEquals(listOf("IFEQ", "IFEQ", "ISUB"), continuation("SourceKt", "twice"))
        assertEquals(listOf("IF_ACMPNE", "ARETURN", "IF_ACMPNE", "ARETURN"), markers("SourceKt", "twice"))
        assertEquals(
            listOf("if_icmple -> if_icmpgt", "iadd -> isub", "if_icmple -> if_icmplt"),
            defaultSites("SourceKt", "twice"),
        )
    }

    @Test
    fun `the Unit a Unit suspend function returns is noise`() {
        assertEquals(listOf("IF_ACMPNE", "ARETURN", "ARETURN"), markers("SourceKt", "store"))
        assertEquals(emptyList<String>(), defaultSites("SourceKt", "store"))
    }

    // A tail call compares with getCOROUTINE_SUSPENDED() directly and has no prologue.
    @Test
    fun `a tail call's marker check is noise`() {
        assertEquals(listOf("IF_ACMPNE", "ARETURN", "ARETURN"), markers("SourceKt", "tailUnit"))
        assertEquals(emptyList<String>(), continuation("SourceKt", "tailUnit"))
    }

    @Test
    fun `a named function's continuation has no sites`() {
        // The re-entry call is not noise: the coroutine and call operators skip it by the continuation's superclass.
        val noise = continuation("SourceKt\$twice\$1", "invokeSuspend")

        assertEquals(true, "IOR" in noise)
        assertEquals(true, "ARETURN" in noise)
        assertEquals(false, "INVOKESTATIC SourceKt.twice" in noise)
        assertEquals(
            emptyList<String>(),
            Operators.all.flatMap { discover(classes.getValue("SourceKt\$twice\$1"), it) },
        )
    }

    @Test
    fun `a suspend lambda keeps the code the user wrote`() {
        // The lambda's Unit return is not a marker: its declared type is in the class signature, which filters do
        // not see. null-returns skips it as equivalent.
        assertEquals(listOf("IF_ACMPNE", "ARETURN"), markers("SourceKt\$launched\$1\$1", "invokeSuspend"))
        assertEquals(emptyList<String>(), continuation("SourceKt\$launched\$1\$1", "invokeSuspend"))
        assertEquals(
            listOf("if_icmple -> if_icmpgt", "imul -> idiv", "if_icmple -> if_icmplt"),
            defaultSites("SourceKt\$launched\$1\$1", "invokeSuspend"),
        )
        // The value runTwice's lambda returns is the user's.
        assertEquals(listOf("areturn -> null"), defaultSites("SourceKt\$runTwice\$1", "invokeSuspend"))
    }

    @Test
    fun `the state machine's resume message is noise`() {
        assertEquals(listOf("LDC"), ResumeMessageFilter.noiseIn(classes, "SourceKt", "twice"))
        assertEquals(listOf("LDC"), ResumeMessageFilter.noiseIn(classes, "SourceKt\$launched\$1\$1", "invokeSuspend"))
        assertEquals(emptyList<Found>(), discover(file, StringsOperator).filter { it.methodName == "twice" })
    }

    @Test
    fun `the same shapes outside a suspend function are user code`() {
        assertEquals(emptyList<String>(), markers("SourceKt", "unitValue"))
        assertEquals(emptyList<String>(), continuation("Counter", "masked"))
        assertEquals(emptyList<String>(), markers("Counter", "masked"))
        assertEquals(listOf("ifeq -> ifne", "isub -> iadd", "ireturn -> 0"), defaultSites("Counter", "masked"))
    }

    @Test
    fun `a Unit returned from a suspend function declared Any is user code`() {
        assertEquals(listOf("IF_ACMPNE", "ARETURN"), markers("SourceKt", "anyUnit"))
    }

    @Test
    fun `a user identity comparison in a suspend function is user code`() {
        assertEquals(listOf("IF_ACMPNE", "ARETURN"), markers("SourceKt", "same"))
        assertEquals(listOf("if_acmpne -> if_acmpeq"), defaultSites("SourceKt", "same"))
    }

    // Owner$State is nested in the owner like the continuation, but it is not the class of the prologue's instanceof.
    @Test
    fun `a user label field in a nested class is user code`() {
        assertEquals(listOf("IFEQ", "IFEQ", "ISUB"), continuation("Owner", "work"))
        assertEquals(listOf("ifeq -> ifne", "isub -> iadd"), defaultSites("Owner", "work"))
    }

    @Test
    fun `ordinals index the filtered sites in discover and mutate`() {
        val site = discover(file, NegateConditionalsOperator).single { it.methodName == "twice" }
        val bytes = mutate(file, NegateConditionalsOperator, site) { null }

        assertEquals(0, site.ordinal)
        // leaf(5) = 6 > 3, so 6 + leaf(6); the mutant takes the else branch.
        assertEquals(13, loadAndInvoke(classes, "SourceKt", "runTwice", 5))
        assertEquals(6, loadAndInvoke(classes + ("SourceKt" to bytes), "SourceKt", "runTwice", 5))
    }

    @Test
    fun `no debug info is needed`() {
        val node = parse(file)
        node.methods.forEach { m ->
            m.localVariables = null
            m.instructions.filterIsInstance<LineNumberNode>().forEach(m.instructions::remove)
        }

        val stripped = classes + ("SourceKt" to node.toBytes())
        assertEquals(continuation("SourceKt", "twice"), ContinuationFilter.noiseIn(stripped, "SourceKt", "twice"))
        assertEquals(markers("SourceKt", "twice"), SuspendMarkerFilter.noiseIn(stripped, "SourceKt", "twice"))
    }
}
