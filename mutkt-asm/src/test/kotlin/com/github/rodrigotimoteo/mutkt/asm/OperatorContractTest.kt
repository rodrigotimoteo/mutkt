package com.github.rodrigotimoteo.mutkt.asm

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter
import java.util.stream.Stream

// New operators go here; switch to Operators.all once it exists.
internal fun operatorsUnderTest(): List<Operator> = listOf(ToyOperator)

internal class OperatorContractTest {
    // Corpus first, then the test runtime classpath (Kotlin stdlib); JDK types fall through to the platform.
    private val hierarchy: Hierarchy = { type ->
        corpus[type.replace('/', '.')]
            ?: javaClass.classLoader.getResourceAsStream("$type.class")?.use { it.readBytes() }
    }

    private val loader =
        object : ClassLoader(OperatorContractTest::class.java.classLoader) {
            override fun findClass(name: String): Class<*> =
                corpus[name]?.let { defineClass(name, it, 0, it.size) } ?: throw ClassNotFoundException(name)
        }

    // getDeclaredMethods links the class, which runs the JVM verifier (and so checks the StackMapTable
    // HierarchyClassWriter wrote). Limit: static initializers are not run, so only linking is covered.
    private fun linkInFreshLoader(
        name: String,
        bytes: ByteArray,
    ) {
        val fresh = ChildFirstLoader(corpus + (name to bytes), OperatorContractTest::class.java.classLoader)
        fresh.loadClass(name).declaredMethods
    }

    private fun sitesOf(op: Operator) = corpus.values.flatMap { bytes -> discover(bytes, op).map { bytes to it } }

    private fun verifyLog(bytes: ByteArray) =
        StringWriter()
            .also { CheckClassAdapter.verify(ClassReader(bytes), loader, false, PrintWriter(it)) }
            .toString()

    private fun plainRewrite(bytes: ByteArray) =
        HierarchyClassWriter(hierarchy).also { parse(bytes).accept(it) }.toByteArray()

    @ParameterizedTest
    @MethodSource("operators")
    fun `finds at least one site in the corpus`(op: Operator) {
        assertTrue(sitesOf(op).isNotEmpty(), "${op.id} found no site in the corpus")
    }

    @ParameterizedTest
    @MethodSource("operators")
    fun `every site mutates into a changed, verifiable class`(op: Operator) {
        sitesOf(op).forEach { (bytes, site) ->
            val mutant = mutate(bytes, op, site, hierarchy)

            assertFalse(mutant.contentEquals(plainRewrite(bytes)), "$site left the class unchanged")
            assertEquals("", verifyLog(mutant), "$site produced an invalid class")
            linkInFreshLoader(site.className.replace('/', '.'), mutant)
        }
    }

    @ParameterizedTest
    @MethodSource("operators")
    fun `applying a site twice gives identical bytes`(op: Operator) {
        sitesOf(op).forEach { (bytes, site) ->
            val first = mutate(bytes, op, site, hierarchy)

            assertEquals(discover(bytes, op), discover(bytes, op))

            assertArrayEquals(first, mutate(bytes, op, site, hierarchy), "$site is not deterministic")
        }
    }

    private class ChildFirstLoader(
        private val classes: Map<String, ByteArray>,
        parent: ClassLoader,
    ) : ClassLoader(parent) {
        override fun loadClass(
            name: String,
            resolve: Boolean,
        ): Class<*> =
            synchronized(getClassLoadingLock(name)) {
                val bytes = classes[name] ?: return super.loadClass(name, resolve)
                findLoadedClass(name) ?: defineClass(name, bytes, 0, bytes.size)
            }
    }

    companion object {
        @JvmStatic
        fun operators(): Stream<Arguments> =
            operatorsUnderTest().stream().map { Arguments.of(Named.of(it.id.value, it)) }
    }
}
