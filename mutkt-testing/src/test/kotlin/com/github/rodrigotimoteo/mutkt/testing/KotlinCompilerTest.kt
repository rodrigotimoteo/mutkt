package com.github.rodrigotimoteo.mutkt.testing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

private const val JUPITER_SOURCE = """
package p
import org.junit.jupiter.api.Test
class SampleTest { @Test fun works() {} }
"""

private const val COMPOSE_SOURCE = """
package p
import androidx.compose.runtime.Composable
@Composable fun Greeting(name: String) { name.length }
"""

class KotlinCompilerTest {
    @Test
    fun `compiles source to class bytes keyed by binary name`() {
        val classes = compileKotlin("package p\nclass A\nclass B")

        assertEquals(setOf("p.A", "p.B"), classes.keys)
        assertTrue(classes.values.all { it.take(4) == listOf(0xCA, 0xFE, 0xBA, 0xBE).map(Int::toByte) })
    }

    @Test
    fun `compiled top level function runs`() {
        val classes = compileKotlin("package p\nfun twice(x: Int) = x * 2")

        assertEquals(10, loadAndInvoke(classes, "p.SourceKt", "twice", 5))
    }

    @Test
    fun `inherited classpath compiles Jupiter tests`() {
        val classes = compileKotlin(JUPITER_SOURCE, inheritClassPath = true)

        assertEquals(setOf("p.SampleTest"), classes.keys)
    }

    @Test
    fun `own classpath only fails with unresolved reference`() {
        val error = assertThrows<IllegalStateException> { compileKotlin(JUPITER_SOURCE) }

        assertTrue(error.message.orEmpty().contains("Unresolved reference"), error.message)
    }

    @Test
    fun `compiler arguments reach kotlinc`() {
        val source = "package p\ninterface I { fun one() = 1 }"

        val compiled = { mode: String -> compileKotlin(source, kotlincArguments = listOf("-Xjvm-default=$mode")).keys }

        assertEquals(setOf("p.I", "p.I\$DefaultImpls"), compiled("disable"))
        assertEquals(setOf("p.I"), compiled("all"))
    }

    @Test
    fun `compose source goes through the Compose compiler plugin`() {
        val classes = compileCompose(COMPOSE_SOURCE)

        val descriptors = mutableListOf<String>()
        val visitor =
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ) = null.also { if (name == "Greeting") descriptors += descriptor }
            }
        ClassReader(classes.getValue("p.SourceKt")).accept(visitor, 0)

        assertEquals(listOf("(Ljava/lang/String;Landroidx/compose/runtime/Composer;I)V"), descriptors)
    }

    @Test
    fun `invalid source fails fast`() {
        assertThrows<IllegalStateException> { compileKotlin("fun broken( =") }
    }
}
