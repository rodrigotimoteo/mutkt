package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import java.util.concurrent.Executors

class AsmMutantGeneratorTest {
    private val classes =
        compileKotlin(
            """
            package a.b

            fun add(a: Int, b: Int) = a + b
            fun add(a: Int, b: Int, c: Int) = a + b + c
            fun subtract(a: Int, b: Int) = a - b

            class Outer {
                fun plus(a: Int, b: Int) = a + b
                class Inner { fun plus(a: Int, b: Int) = a + b }
            }
            """.trimIndent(),
        )
    private val files = classes.map { (name, bytes) -> ClassFile(name, bytes) }
    private val sourceKt = "a.b.SourceKt"

    private object ToyMultiply : Operator {
        override val id = OperatorId("toy-add-to-mul")

        override fun sites(
            method: MethodNode,
            cls: ClassInfo,
        ) = ToyOperator.sites(method, cls)

        override fun apply(
            method: MethodNode,
            site: Site,
        ) = method.instructions.set(site.instruction, InsnNode(Opcodes.IMUL))
    }

    private object ToyNoChange : Operator {
        override val id = OperatorId("toy-no-change")

        override fun sites(
            method: MethodNode,
            cls: ClassInfo,
        ) = ToyOperator.sites(method, cls)

        override fun apply(
            method: MethodNode,
            site: Site,
        ) = Unit
    }

    private fun generator(vararg ops: Operator = arrayOf(ToyOperator)) = AsmMutantGenerator(ops.toList()) { null }

    private fun Mutant.pick() = Triple(className, methodName + methodDescriptor, id.ordinal)

    @Test
    fun `mutants carry the compiled source file name`() {
        assertEquals(setOf("Source.kt"), generator().discover(files).map { it.sourceFile }.toSet())
    }

    @Test
    fun `overloads get distinct ids`() {
        val mutants = generator().discover(files).filter { it.className == sourceKt && it.methodName == "add" }

        assertEquals(
            listOf(
                Triple(sourceKt, "add(II)I", 0),
                Triple(sourceKt, "add(III)I", 0),
                Triple(sourceKt, "add(III)I", 1),
            ),
            mutants.map { it.pick() },
        )
        assertEquals(mutants.size, mutants.map { it.id }.toSet().size)
    }

    @Test
    fun `two runs give equal lists regardless of input order`() {
        assertEquals(generator().discover(files), generator().discover(files.reversed()))
    }

    @Test
    fun `class names are dotted and inner classes keep their dollar`() {
        val mutants = generator().discover(files)
        val inner = mutants.first { it.className == "a.b.Outer\$Inner" }

        assertEquals(setOf(sourceKt, "a.b.Outer", "a.b.Outer\$Inner"), mutants.map { it.className }.toSet())
        assertEquals("toy-add-to-sub:a.b.Outer\$Inner#plus(II)I:0", inner.id.toString())
    }

    @Test
    fun `mutants come ordered by class then operator`() {
        val mutants = generator(ToyOperator, ToyMultiply).discover(files)

        assertEquals(mutants.map { it.className }, mutants.map { it.className }.sorted())
        val inSource = mutants.filter { it.className == sourceKt }.map { it.operator.value }
        assertEquals(listOf("toy-add-to-sub", "toy-add-to-mul"), inSource.distinct())
        assertEquals(inSource.sortedBy { if (it == "toy-add-to-sub") 0 else 1 }, inSource)
    }

    @Test
    fun `materialize returns bytes that load and behave differently`() {
        val generator = generator()
        val mutant = generator.discover(files).single { it.className == sourceKt && it.methodDescriptor == "(II)I" }

        val bytes = generator.materialize(mutant)

        assertEquals(8, loadAndInvoke(classes, sourceKt, "add", 5, 3))
        assertEquals(2, loadAndInvoke(classes + (sourceKt to bytes), sourceKt, "add", 5, 3))
    }

    @Test
    fun `materialize is repeatable`() {
        val generator = generator()
        val mutant = generator.discover(files).first()

        assertArrayEquals(generator.materialize(mutant), generator.materialize(mutant))
    }

    @Test
    fun `materialize can run in parallel`() {
        val generator = generator()
        val mutants = generator.discover(files)
        val expected = mutants.map { generator.materialize(it) }
        val pool = Executors.newFixedThreadPool(8)

        val actual =
            try {
                (1..4)
                    .flatMap {
                        mutants.map { m ->
                            pool.submit<ByteArray> { generator.materialize(m) }
                        }
                    }.map { it.get() }
            } finally {
                pool.shutdown()
            }

        actual.forEachIndexed { i, bytes -> assertArrayEquals(expected[i % expected.size], bytes) }
    }

    @Test
    fun `an unknown mutant fails`() {
        val generator = generator()
        val other = generator.discover(files).first().let { it.copy(id = it.id.copy(ordinal = 99)) }

        val error = assertThrows<IllegalArgumentException> { generator.materialize(other) }

        assertTrue(error.message!!.contains("Unknown mutant"), error.message)
    }

    @Test
    fun `materializing before discovering fails`() {
        val mutant = generator().discover(files).first()

        assertThrows<IllegalStateException> { generator().materialize(mutant) }
    }

    @Test
    fun `duplicate class names fail instead of dropping mutants`() {
        val error =
            assertThrows<IllegalArgumentException> {
                generator().discover(
                    files + files.first { it.name == sourceKt },
                )
            }

        assertTrue(error.message!!.contains("Duplicate"), error.message)
    }

    @Test
    fun `duplicate operator ids fail instead of dropping mutants`() {
        val error = assertThrows<IllegalArgumentException> { generator(ToyOperator, ToyOperator).discover(files) }

        assertTrue(error.message!!.contains("Duplicate"), error.message)
    }

    @Test
    fun `classes without sites yield nothing`() {
        assertEquals(emptyList<Mutant>(), generator().discover(files).filter { it.methodName == "subtract" })
        assertEquals(emptyList<Mutant>(), generator().discover(emptyList()))
    }

    @Test
    fun `an operator that leaves the class unchanged yields no mutants`() {
        assertEquals(emptyList<Mutant>(), generator(ToyNoChange).discover(files))
    }

    @Test
    fun `an unchanged operator does not hide the others`() {
        val mixed = generator(ToyNoChange, ToyOperator).discover(files)

        assertEquals(generator(ToyOperator).discover(files), mixed)
        assertTrue(mixed.none { it.operator == ToyNoChange.id })
    }

    // The compiler casts both branches to Animal, which hides the merge; drop the casts so frames
    // must join Dog and Cat.
    private fun uncastPick(): Map<String, ByteArray> {
        val pets =
            compileKotlin(
                """
                package a.b

                open class Animal { fun legs() = 4 }
                class Dog : Animal()
                class Cat : Animal()
                fun pick(flag: Boolean, a: Int): Int {
                    val pet: Animal = if (flag) Dog() else Cat()
                    return pet.legs() + a
                }
                """.trimIndent(),
            )
        val node = parse(pets.getValue(sourceKt))
        node.methods.filter { it.name == "pick" }.forEach { m ->
            val casts = m.instructions.filterIsInstance<TypeInsnNode>().filter { it.opcode == Opcodes.CHECKCAST }
            casts.forEach(m.instructions::remove)
        }
        return pets + (sourceKt to node.toBytes())
    }

    @Test
    fun `frames join project types from the analysed classes`() {
        val pets = uncastPick().map { (name, bytes) -> ClassFile(name, bytes) }
        val generator = generator()

        val mutant = generator.discover(pets).single()

        assertTrue(generator.materialize(mutant).isNotEmpty())
        assertThrows<IllegalStateException> { generator().discover(pets.filter { it.name == sourceKt }) }
    }

    @Test
    fun `frames join dependency types through the classpath lookup by internal name`() {
        val pets = uncastPick()
        val internal = pets.mapKeys { it.key.replace('.', '/') }
        val onlyPick = listOf(ClassFile(sourceKt, pets.getValue(sourceKt)))

        assertEquals(1, AsmMutantGenerator(listOf(ToyOperator)) { internal[it] }.discover(onlyPick).size)
    }
}
