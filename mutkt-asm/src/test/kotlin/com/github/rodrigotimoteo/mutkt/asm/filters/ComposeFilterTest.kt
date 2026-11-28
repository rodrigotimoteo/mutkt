package com.github.rodrigotimoteo.mutkt.asm.filters

import com.github.rodrigotimoteo.mutkt.asm.Operators
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.parse
import com.github.rodrigotimoteo.mutkt.asm.toBytes
import com.github.rodrigotimoteo.mutkt.testing.compileCompose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Real Compose compiler output (Kotlin's Compose plugin, Compose runtime 1.7).
class ComposeFilterTest {
    private val classes =
        compileCompose(
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.remember

            @Composable fun Text(s: String) { s.length }
            @Composable fun Greeting(name: String, count: Int) { if (count > 3) Text(name + count) else Text(name) }
            @Composable fun WithDefault(n: Int = 5, label: String = "x") { if (n > 2) Text(label) }
            @Composable fun Remembered(n: Int): Int { val x = remember(n) { if (n > 0) n * 2 else 0 }; return x + 1 }
            @Composable fun Holder(content: @Composable () -> Unit) { content() }
            @Composable fun UsesLambda(n: Int) { Holder { if (n > 1) Text("a") } }
            @Composable fun NoKey(n: Int): Int { val x = remember { n * 3 }; return x - 1 }
            @Composable fun TwoKeys(a: Int, b: String): Int { val x = remember(a, b) { a + b.length }; return x * 2 }
            class Model(val x: Int)
            @Composable fun Stable(m: Model) { if (m.x > 1) Text("y") }
            fun masked(changed: Int): Boolean = changed and 6 != 0
            """.trimIndent(),
        )

    private fun sites(
        className: String,
        method: String,
        bytes: ByteArray = classes.getValue(className),
    ) = Operators.all
        .flatMap { discover(bytes, it) }
        .filter { it.methodName == method }
        .map { it.description }
        .sorted()

    @Test
    fun `only the user's branches and calls stay in a composable`() {
        assertEquals(
            listOf(
                "body -> return",
                "if_icmple -> if_icmpgt",
                "if_icmple -> if_icmplt",
                "remove Intrinsics.checkNotNullParameter",
                "remove call SourceKt.Text",
                "remove call SourceKt.Text",
            ),
            sites("SourceKt", "Greeting"),
        )
    }

    @Test
    fun `default parameter masks are noise, the default values are not`() {
        assertEquals(
            listOf("body -> return", "if_icmple -> if_icmpgt", "if_icmple -> if_icmplt", "remove call SourceKt.Text"),
            sites("SourceKt", "WithDefault"),
        )
    }

    @Test
    fun `remember keeps only the calculation the user wrote`() {
        assertEquals(
            listOf(
                "body -> return 0",
                "iadd -> isub",
                "ifle -> ifgt",
                "ifle -> iflt",
                "imul -> idiv",
                "ireturn -> 0",
                "replace call with default: Integer.valueOf",
                "replace call with default: Number.intValue",
            ),
            sites("SourceKt", "Remembered"),
        )
    }

    @Test
    fun `remember without a key keeps only the calculation`() {
        assertEquals(
            listOf(
                "body -> return 0",
                "imul -> idiv",
                "ireturn -> 0",
                "isub -> iadd",
                "replace call with default: Integer.valueOf",
                "replace call with default: Number.intValue",
            ),
            sites("SourceKt", "NoKey"),
        )
    }

    // The keys' changed() results are ORed into a local, and the jump tests that local.
    @Test
    fun `remember with two keys keeps only the calculation`() {
        assertEquals(
            listOf(
                "body -> return 0",
                "iadd -> isub",
                "imul -> idiv",
                "ireturn -> 0",
                "remove Intrinsics.checkNotNullParameter",
                "replace call with default: Integer.valueOf",
                "replace call with default: Number.intValue",
                "replace call with default: String.length",
            ),
            sites("SourceKt", "TwoKeys"),
        )
    }

    @Test
    fun `remember is recognised without debug info`() {
        val stripped =
            parse(classes.getValue("SourceKt")).also { node ->
                node.methods.forEach {
                    it.localVariables =
                        null
                }
            }
        val bytes = stripped.toBytes()

        listOf("Remembered", "NoKey", "TwoKeys").forEach { method ->
            assertEquals(sites("SourceKt", method), sites("SourceKt", method, bytes), method)
        }
    }

    // Kotlin 2.1's plugin skips by default (strong skipping), so a class parameter gets a changed() check too.
    @Test
    fun `a stable class parameter keeps only the user's code`() {
        assertEquals(
            listOf(
                "body -> return",
                "if_icmple -> if_icmpgt",
                "if_icmple -> if_icmplt",
                "remove Intrinsics.checkNotNullParameter",
                "remove call SourceKt.Text",
                "replace call with default: Model.getX",
            ),
            sites("SourceKt", "Stable"),
        )
    }

    // Kotlin 2.x compiles the composable lambda to a static SourceKt.UsesLambda$lambda$0 behind invokedynamic.
    @Test
    fun `a composable lambda keeps its own branch`() {
        assertEquals(
            listOf(
                "body -> return null",
                "if_icmple -> if_icmpgt",
                "if_icmple -> if_icmplt",
                "remove call SourceKt.Text",
            ),
            sites("SourceKt", "UsesLambda\$lambda\$0"),
        )
    }

    @Test
    fun `the restart lambdas the plugin generates have no sites`() {
        val restart = listOf("Greeting\$lambda\$0", "UsesLambda\$lambda\$1")
        val methods = parse(classes.getValue("SourceKt")).methods.map { it.name }

        assertTrue(methods.containsAll(restart), methods.toString())
        restart.forEach { assertEquals(emptyList<String>(), sites("SourceKt", it), it) }
    }

    // The lambda itself is an invokedynamic (no site); rememberComposableLambda around it is plugin code.
    @Test
    fun `a composable that passes a lambda keeps the call it makes`() {
        assertEquals(listOf("body -> return", "remove call SourceKt.Holder"), sites("SourceKt", "UsesLambda"))
    }

    @Test
    fun `bit tests outside a composable are not noise`() {
        assertEquals(emptyList<String>(), ComposeFilter.noiseIn(classes, "SourceKt", "masked"))
    }
}
