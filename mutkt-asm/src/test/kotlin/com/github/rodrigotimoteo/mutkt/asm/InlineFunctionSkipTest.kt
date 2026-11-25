package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.asm.filters.InlinedBodyFilter
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

class InlineFunctionSkipTest {
    private val classes =
        compileKotlin(
            """
            inline fun twice(a: Int): Int = a + a
            private inline fun hidden(a: Int): Int = a + a + 1
            inline fun <reified T> kind(x: Any): Boolean = x is T
            inline val Int.dbl: Int get() = this + this
            fun ordinary(a: Int): Int = a + 1
            fun caller(n: Int): Int = twice(n) + hidden(n) + n.dbl + 1
            fun ref(): (Int) -> Int = ::twice
            """.trimIndent(),
        )
    private val file = classes.getValue("SourceKt")

    // Regular methods named like the inline function they call: the marker of the inlined copy carries their name.
    private val sameName =
        compileKotlin(
            """
            inline fun foo(x: Int): Int = x + x
            fun foo(): Int = foo(1) + 1
            class Bag(val items: List<Int>) {
                fun map(f: (Int) -> Int) = items.map(f)
                internal inline fun scale(a: Int) = a + a
            }
            fun forEach(l: List<Int>): Int { var s = 0; l.forEach { s += it }; return s }
            fun filter(l: List<Int>) = l.filter { it > 1 }
            fun let(a: Int) = a.let { it + 1 }
            @JvmInline value class V(val x: Int)
            inline fun mangled(v: V): Int = v.x + v.x
            """.trimIndent(),
        )

    private fun method(
        className: String,
        name: String,
        desc: String? = null,
    ): MethodNode =
        parse(sameName.getValue(className)).methods.single {
            it.name.startsWith(name) &&
                (desc == null || it.desc == desc)
        }

    private fun noiseOpcodes(
        className: String,
        method: MethodNode,
    ) = InlinedBodyFilter.noise(className, method).map { it.opcode }

    @Test
    fun `no inline function body is mutated, ordinary code and callers are`() {
        val found = discover(file, ToyOperator)

        assertEquals(setOf("ordinary", "caller"), found.map { it.methodName }.toSet())
        assertEquals(listOf(0), found.filter { it.methodName == "ordinary" }.map { it.ordinal })
        assertEquals(listOf(0, 1, 2), found.filter { it.methodName == "caller" }.map { it.ordinal })
    }

    @Test
    fun `inline function methods are not mutable`() {
        val methods = parse(file).methods.associate { it.name to isMutable(it) }

        assertEquals(false, methods.getValue("twice"))
        assertEquals(false, methods.getValue("hidden"))
        assertEquals(false, methods.getValue("kind"))
        assertEquals(false, methods.getValue("getDbl"))
        assertEquals(true, methods.getValue("ordinary"))
    }

    // Documented limit: the marker is a local variable, so a class compiled without debug info is still mutated.
    @Test
    fun `without debug info an inline function is still mutated`() {
        val node = parse(file).also { n -> n.methods.forEach { it.localVariables = null } }

        val names = discover(node.toBytes(), ToyOperator).map { it.methodName }.toSet()

        assertEquals(setOf("twice", "hidden", "getDbl", "ordinary", "caller"), names)
    }

    @Test
    fun `a regular method named like the inline function it calls stays mutable and its inlined copy is noise`() {
        val foo = method("SourceKt", "foo", "()I")
        val found = discover(sameName.getValue("SourceKt"), ToyOperator).filter { it.methodName == "foo" }

        assertEquals(true, isMutable(foo))
        assertEquals(1, noiseOpcodes("SourceKt", foo).count { it == Opcodes.IADD })
        assertEquals(listOf("()I"), found.map { it.descriptor })
        assertEquals(false, isMutable(method("SourceKt", "foo", "(I)I")))
    }

    @Test
    fun `standard library inline functions called by a method of the same name are noise there`() {
        val noise =
            listOf(method("Bag", "map"), method("SourceKt", "forEach"), method("SourceKt", "filter")).map {
                isMutable(it) to noiseOpcodes(if (it.name == "map") "Bag" else "SourceKt", it).isNotEmpty()
            }

        assertEquals(List(3) { true to true }, noise)
        assertEquals(true, isMutable(method("SourceKt", "let")))
        assertEquals(1, discover(sameName.getValue("SourceKt"), ToyOperator).count { it.methodName == "let" })
    }

    @Test
    fun `internal and value-class mangled inline functions are not mutable`() {
        assertEquals(false, isMutable(method("Bag", "scale")))
        assertEquals(false, isMutable(method("SourceKt", "mangled")))
    }

    // The reference compiles to a class whose invoke holds a copy of the body: the original is never called.
    @Test
    fun `a function reference does not call the inline function`() {
        val calls =
            classes.values
                .flatMap { parse(it).methods }
                .flatMap { it.instructions }
                .filterIsInstance<MethodInsnNode>()
                .filter { it.name == "twice" }

        assertEquals(emptyList<String>(), calls.map { it.owner })
    }
}
