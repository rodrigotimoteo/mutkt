package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.asm.operators.ArithmeticOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.VoidMethodCallsOperator
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes.BIPUSH
import org.objectweb.asm.Opcodes.IADD
import org.objectweb.asm.Opcodes.ISUB
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter

class FinallyMutantsTest {
    private val bytes = finallyClasses.getValue("SourceKt")
    private val hierarchy: Hierarchy = { type -> finallyClasses[type.replace('/', '.')] }
    private val loader =
        object : ClassLoader(FinallyMutantsTest::class.java.classLoader) {
            override fun findClass(name: String): Class<*> =
                finallyClasses[name]?.let { defineClass(name, it, 0, it.size) } ?: throw ClassNotFoundException(name)
        }

    private fun sites(
        op: Operator,
        method: String,
        base: ByteArray = bytes,
    ) = discover(base, op).filter { it.methodName == method }

    private fun withMutated(
        base: ByteArray,
        op: Operator,
        site: Found,
    ) = finallyClasses + ("SourceKt" to mutate(base, op, site, hierarchy))

    // The classes with the `index`th site of [op] on the source line holding [text] applied.
    private fun mutated(
        op: Operator,
        method: String,
        text: String,
        index: Int = 0,
    ) = withMutated(bytes, op, sites(op, method).filter { it.line == lineOf(text) }[index])

    private fun run(
        classes: Map<String, ByteArray>,
        probe: String,
        vararg args: Any?,
    ) = loadAndInvoke(classes, "SourceKt", probe, *args)

    private fun verifyLog(bytes: ByteArray) =
        StringWriter().also { CheckClassAdapter.verify(ClassReader(bytes), loader, false, PrintWriter(it)) }.toString()

    @Test
    fun `each finally body is one mutant, the code around it keeps its own`() {
        val expected =
            mapOf(
                "tf" to 4,
                "loopTf" to 2,
                "tcf" to 3,
                "nested" to 3,
                "closing" to 1,
                "same" to 2,
                "finIf" to 1,
                "finRet" to 3,
                "finLambda" to 2,
                "finLocal" to 2,
                "lookalike" to 2,
                "sync" to 1,
            )

        assertEquals(expected, expected.keys.associateWith { sites(ArithmeticOperator, it).size })
    }

    @Test
    fun `two sites on one line stay two mutants with their own behavior`() {
        val first = mutated(ArithmeticOperator, "same", "a > b", 0)
        val second = mutated(ArithmeticOperator, "same", "a > b", 1)

        assertEquals(listOf(6, 6), listOf(run(finallyClasses, "same", 5, 3), run(finallyClasses, "same", 3, 5)))
        assertEquals(listOf(4, 6), listOf(run(first, "same", 5, 3), run(first, "same", 3, 5)))
        assertEquals(listOf(6, 4), listOf(run(second, "same", 5, 3), run(second, "same", 3, 5)))
    }

    @Test
    fun `the finally mutant changes the early return, normal and exception paths`() {
        val mutant = mutated(ArithmeticOperator, "tf", "counter + 100")

        assertEquals(listOf(100, 101, 100), listOf(10, 1, -1).map { run(finallyClasses, "probe", it) })
        assertEquals(listOf(-100, -99, -100), listOf(10, 1, -1).map { run(mutant, "probe", it) })
    }

    @Test
    fun `break, continue and normal exits of a loop change together`() {
        val mutant = mutated(ArithmeticOperator, "loopTf", "counter + 200")

        assertEquals(1200, run(finallyClasses, "probeLoop", 8))
        assertEquals(-1200, run(mutant, "probeLoop", 8))
    }

    @Test
    fun `a catch exit and the catch-any exit change with the try exit`() {
        val mutant = mutated(ArithmeticOperator, "tcf", "counter + 300")

        assertEquals(listOf(300, 299, 300), listOf("5", "x", null).map { run(finallyClasses, "probeTcf", it) })
        assertEquals(listOf(-300, -301, -300), listOf("5", "x", null).map { run(mutant, "probeTcf", it) })
    }

    @Test
    fun `nested finally bodies mutate independently`() {
        val inner = mutated(ArithmeticOperator, "nested", "counter + 400")
        val outer = mutated(ArithmeticOperator, "nested", "counter + 500")

        assertEquals(listOf(900, 901, 900), listOf(10, 1, -1).map { run(finallyClasses, "probeNested", it) })
        assertEquals(listOf(100, 101, 100), listOf(10, 1, -1).map { run(inner, "probeNested", it) })
        assertEquals(listOf(-100, -99, -100), listOf(10, 1, -1).map { run(outer, "probeNested", it) })
    }

    @Test
    fun `dropping closeFinally leaves the resource open on both paths`() {
        val mutant = mutated(VoidMethodCallsOperator, "probeClosing", "try { r.use")

        assertEquals(listOf(true, true), listOf(false, true).map { run(finallyClasses, "probeClosing", it) })
        assertEquals(listOf(false, false), listOf(false, true).map { run(mutant, "probeClosing", it) })
    }

    @Test
    fun `a finally with control flow, a local or a lambda changes on every path`() {
        val branch = mutated(ArithmeticOperator, "finIf", "counter + 700")
        val local = mutated(ArithmeticOperator, "finLocal", "a + 1000")
        val lambda = mutated(ArithmeticOperator, "finLambda", "it + 900", 1)

        assertEquals(listOf(700, 700, 0), listOf(10, 4, 1).map { run(finallyClasses, "probeIf", it) })
        assertEquals(listOf(-700, -700, 0), listOf(10, 4, 1).map { run(branch, "probeIf", it) })
        assertEquals(listOf(1010, 1001), listOf(10, 1).map { run(finallyClasses, "probeLocal", it) })
        assertEquals(listOf(-990, -999), listOf(10, 1).map { run(local, "probeLocal", it) })
        assertEquals(listOf(1803, 1803), listOf(10, 1).map { run(finallyClasses, "probeLambda", it) })
        assertEquals(listOf(-1797, -1797), listOf(10, 1).map { run(lambda, "probeLambda", it) })
    }

    @Test
    fun `every mutant of every operator on the finally fixtures verifies`() {
        val invalid =
            Operators.all
                .flatMap { op ->
                    discover(bytes, op).map { it to verifyLog(mutate(bytes, op, it, hierarchy)) }
                }.filter { it.second.isNotEmpty() }

        assertEquals(emptyList<Pair<Found, String>>(), invalid)
    }

    // IADD to ISUB with a description that tells the copies apart, as an operator that looks at its context would.
    private object PositionOperator : Operator {
        override val id = OperatorId("position")

        override fun sites(method: MethodNode): List<Site> =
            method.instructions.filter { it.opcode == IADD }.map {
                Site(
                    it,
                    "iadd at ${method.instructions.indexOf(it)}",
                )
            }

        override fun apply(
            method: MethodNode,
            site: Site,
        ) = method.instructions.set(site.instruction, InsnNode(ISUB))
    }

    @Test
    fun `a twin the operator describes differently keeps its original code`() {
        val mutant = mutated(PositionOperator, "tf", "counter + 100")

        assertEquals(listOf(-100, 101, 100), listOf(10, 1, -1).map { run(mutant, "probe", it) })
    }

    // Changes the constant of the first copy of the finally body of tf, so the copies are no longer the same code.
    private val differing =
        parse(bytes)
            .also { node ->
                val update = node.methods.single { it.name == "tf" }.instructions
                update.filterIsInstance<IntInsnNode>().first { it.opcode == BIPUSH && it.operand == 100 }.operand = 101
            }.toBytes()

    @Test
    fun `when one copy differs the copies stay separate mutants`() {
        val copies = sites(ArithmeticOperator, "tf", differing).filter { it.line == lineOf("counter + 100") }
        val changed =
            copies.map { site ->
                val mutant = withMutated(differing, ArithmeticOperator, site)
                listOf(10, 1, -1).map {
                    run(mutant, "probe", it) !=
                        run(finallyClasses + ("SourceKt" to differing), "probe", it)
                }
            }

        assertEquals(3, copies.size)
        assertEquals(
            listOf(listOf(true, false, false), listOf(false, true, false), listOf(false, false, true)),
            changed,
        )
    }

    @Test
    fun `a finally inside a finally falls back to a mutant per copy`() {
        val original = listOf(10, 1).map { run(finallyClasses, "probeInFinally", it) }
        val sites = sites(ArithmeticOperator, "inFinally")
        val changed =
            listOf(10, 1).map { a ->
                sites.count {
                    run(withMutated(bytes, ArithmeticOperator, it), "probeInFinally", a) !=
                        original[listOf(10, 1).indexOf(a)]
                }
            }

        assertEquals(6, sites.size)
        assertEquals(listOf(2, 2), changed)
    }
}
