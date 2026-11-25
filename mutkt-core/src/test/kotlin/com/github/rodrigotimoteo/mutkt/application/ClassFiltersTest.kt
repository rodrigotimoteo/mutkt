package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantId
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClassFiltersTest {
    private fun globs(vararg patterns: String) = patterns.map(::Glob)

    private fun mutant(
        className: String,
        ordinal: Int,
    ): Mutant {
        val operator = OperatorId("negate")
        return Mutant(
            MutantId(operator, className, "run", "()V", ordinal),
            operator,
            className,
            "run",
            "()V",
            null,
            "d",
        )
    }

    @Test
    fun `no filters accept everything`() {
        val filters = ClassFilters()
        assertTrue(filters.acceptsClass("any.Thing"))
        assertTrue(filters.acceptsMethod("any.Thing", "run"))
    }

    private data class ClassCase(
        val includes: List<String>,
        val excludes: List<String>,
        val className: String,
        val expected: Boolean,
    )

    @Test
    fun `class acceptance follows includes and excludes`() {
        val cases =
            listOf(
                ClassCase(listOf("com.acme.**"), emptyList(), "com.acme.Foo", true),
                ClassCase(listOf("com.acme.**"), emptyList(), "com.other.Foo", false),
                ClassCase(emptyList(), listOf("**Test"), "com.acme.FooTest", false),
                ClassCase(emptyList(), listOf("**Test"), "com.acme.Foo", true),
                ClassCase(listOf("com.acme.**"), listOf("com.acme.internal.**"), "com.acme.internal.X", false),
                ClassCase(listOf("com.acme.**"), listOf("com.acme.internal.**"), "com.acme.api.X", true),
                ClassCase(listOf("com.acme.*"), emptyList(), "com.acme.sub.X", false),
            )
        val failures =
            cases.filter {
                ClassFilters(globs(*it.includes.toTypedArray()), globs(*it.excludes.toTypedArray()))
                    .acceptsClass(it.className) != it.expected
            }
        assertEquals(emptyList<ClassCase>(), failures)
    }

    @Test
    fun `excluded methods match exactly by name or by class and name`() {
        val filters = ClassFilters(excludedMethods = setOf("get", "com.acme.Foo#toString"))
        // class, method, expected
        val cases =
            listOf(
                Triple("a.B", "get", false),
                Triple("a.B", "budget", true),
                Triple("a.B", "getter", true),
                Triple("a.B", "toString", true),
                Triple("com.acme.Foo", "toString", false),
                Triple("com.acme.Foo", "toStrings", true),
                Triple("com.acme.Foo2", "toString", true),
                Triple("a.com.acme.Foo", "toString", true),
            )
        val failures = cases.filter { (cls, method, expected) -> filters.acceptsMethod(cls, method) != expected }
        assertEquals(emptyList<Triple<String, String, Boolean>>(), failures)
    }

    @Test
    fun `limit keeps the first n per class and preserves overall order`() {
        val mutants =
            listOf(
                mutant("a.A", 0),
                mutant("a.B", 0),
                mutant("a.A", 1),
                mutant("a.A", 2),
                mutant("a.B", 1),
            )
        val limited = ClassFilters(maxMutantsPerClass = 2).limit(mutants)
        assertEquals(listOf(mutants[0], mutants[1], mutants[2], mutants[4]), limited.kept)
        assertEquals(1, limited.omitted)
    }

    @Test
    fun `limit selection depends on the input order`() {
        val mutants = listOf(mutant("a.A", 0), mutant("a.A", 1), mutant("a.A", 2))
        val filters = ClassFilters(maxMutantsPerClass = 1)
        assertEquals(listOf(mutants[0]), filters.limit(mutants).kept)
        assertEquals(listOf(mutants[2]), filters.limit(mutants.reversed()).kept)
    }

    @Test
    fun `limit without a maximum returns all mutants`() {
        val mutants = listOf(mutant("a.A", 0), mutant("a.A", 1))
        assertEquals(Limited(mutants, 0), ClassFilters().limit(mutants))
        assertEquals(Limited(emptyList(), 0), ClassFilters(maxMutantsPerClass = 1).limit(emptyList()))
    }

    @Test
    fun `invalid configuration fails at construction`() {
        // Failure message expected per invalid argument.
        val cases =
            mapOf(
                { ClassFilters(maxMutantsPerClass = 0) } to "maxMutantsPerClass must be positive: 0",
                { ClassFilters(maxMutantsPerClass = -3) } to "maxMutantsPerClass must be positive: -3",
                { ClassFilters(excludedMethods = setOf(" ")) } to "Excluded method must not be blank",
                { ClassFilters(excludedMethods = setOf("#run")) } to "Excluded method needs a class and a method: #run",
                { ClassFilters(excludedMethods = setOf("a.B#")) } to "Excluded method needs a class and a method: a.B#",
                { ClassFilters(excludedMethods = setOf("a#b#c")) } to
                    "Excluded method needs a class and a method: a#b#c",
                { ClassFilters(excludedMethods = setOf(" #run")) } to
                    "Excluded method needs a class and a method:  #run",
            )
        cases.forEach { (build, message) ->
            val error = assertThrows(IllegalArgumentException::class.java) { build() }
            assertEquals(message, error.message)
        }
    }
}
