package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.NoClasses
import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private fun params(count: Int) = (1..count).joinToString { "a$it: Int = $it" }

// kotlinc emits one default mask per 32 parameters, so the bridge of 33 has two.
private val SOURCE =
    """
    import kotlinx.coroutines.runBlocking
    suspend fun many32(${params(32)}): Int = a1 + a32
    suspend fun many33(${params(33)}): Int = a1 + a33
    suspend fun call32(): Int = many32()
    suspend fun call33(): Int = many33()
    fun run32(): Int = runBlocking { call32() }
    fun run33(): Int = runBlocking { call33() }
    fun plain33(${params(33)}): Int = a1 + a33
    fun callPlain33(): Int = plain33()
    """.trimIndent()

class DefaultBridgeCallsTest {
    private val classes = compileKotlin(SOURCE, inheritClassPath = true)

    private fun sites(
        op: Operator,
        method: String,
    ) = discover(classes.getValue("SourceKt"), op).filter { it.methodName == method }

    @Test
    fun `suspend calls through a one-mask and a two-mask default bridge are coroutine sites`() {
        assertEquals(
            listOf("SourceKt.many32\$default"),
            sites(CoroutineOperator, "call32").map {
                it.description.substringAfter(": ")
            },
        )
        assertEquals(
            listOf("SourceKt.many33\$default"),
            sites(CoroutineOperator, "call33").map {
                it.description.substringAfter(": ")
            },
        )
    }

    @Test
    fun `general call removal leaves both bridges to the coroutine operator`() {
        listOf("call32", "call33").forEach {
            assertEquals(
                emptyList<String>(),
                sites(NonVoidMethodCallsOperator, it).map { site ->
                    site.description
                },
                it,
            )
            assertEquals(emptyList<String>(), sites(VoidMethodCallsOperator, it).map { site -> site.description }, it)
        }
    }

    @Test
    fun `a plain method with many defaults is still an ordinary call`() {
        assertEquals(emptyList<String>(), sites(CoroutineOperator, "callPlain33").map { it.description })
        assertEquals(
            listOf("SourceKt.plain33\$default"),
            sites(NonVoidMethodCallsOperator, "callPlain33").map {
                it.description.substringAfter(": ")
            },
        )
    }

    @Test
    fun `the coroutine mutants of both bridges verify and change the result`() {
        listOf(Triple("call32", "run32", 33), Triple("call33", "run33", 34)).forEach { (call, run, expected) ->
            val found = sites(CoroutineOperator, call).single()
            val mutant = mutate(classes.getValue("SourceKt"), CoroutineOperator, found, NoClasses)

            assertEquals(expected, loadAndInvoke(classes, "SourceKt", run), run)
            assertEquals(
                NullPointerException::class.java,
                runCatching {
                    loadAndInvoke(
                        classes + ("SourceKt" to mutant),
                        "SourceKt",
                        run,
                    )
                }.exceptionOrNull()?.javaClass,
            )
        }
    }
}
