package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.domain.MutantId
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class HistoryKeysTest {
    private val fingerprint =
        RunFingerprint(
            pluginVersion = "0.5.0",
            operators = setOf("negate", "math"),
            engines = setOf("junit-jupiter"),
            includeTags = setOf("fast"),
            excludeTags = emptySet(),
            timeout = 60.seconds,
            jvmArgs = listOf("-Xmx1g", "-Da=b"),
            java = "21",
            workingDir = "/p",
            classpath = linkedMapOf("lib/a.jar" to "a1", "lib/b.jar" to "b1"),
            helperTestClasses = mapOf("a.Fixtures" to "f1"),
        )

    private val id = MutantId(OperatorId("negate"), "a.Foo", "run", "(I)V", 0)

    private fun key(
        id: MutantId = this.id,
        bytes: ByteArray = byteArrayOf(1, 2),
        tests: Map<String, String> = mapOf("a.FooTest" to "t1", "a.FooTest\$Nested" to "n1"),
        reached: Map<String, String> = mapOf("a.Foo" to "m1", "a.Bar" to "m2"),
    ) = mutantKey(id, bytes, tests, reached)

    @Test
    fun `equal inputs give the same fingerprint`() =
        assertEquals(fingerprint.digest(), fingerprint.copy(jvmArgs = listOf("-Xmx1g", "-Da=b")).digest())

    @Test
    fun `every fingerprint input changes the fingerprint`() {
        val changed =
            listOf(
                fingerprint.copy(pluginVersion = "0.5.1"),
                fingerprint.copy(operators = setOf("negate")),
                fingerprint.copy(engines = setOf("junit-jupiter", "junit-vintage")),
                fingerprint.copy(includeTags = emptySet()),
                fingerprint.copy(excludeTags = setOf("slow")),
                fingerprint.copy(timeout = 61.seconds),
                fingerprint.copy(jvmArgs = listOf("-Xmx2g", "-Da=b")),
                fingerprint.copy(java = "17"),
                fingerprint.copy(workingDir = "/q"),
                fingerprint.copy(classpath = linkedMapOf("lib/a.jar" to "a2", "lib/b.jar" to "b1")),
                fingerprint.copy(helperTestClasses = mapOf("a.Fixtures" to "f2")),
            ).map { it.digest() }

        assertEquals(changed.size + 1, (changed + fingerprint.digest()).toSet().size)
    }

    @Test
    fun `set order does not change the fingerprint but classpath order does`() {
        assertEquals(fingerprint.digest(), fingerprint.copy(operators = setOf("math", "negate")).digest())
        val reordered = linkedMapOf("lib/b.jar" to "b1", "lib/a.jar" to "a1")
        assertNotEquals(fingerprint.digest(), fingerprint.copy(classpath = reordered).digest())
    }

    @Test
    fun `values never run into the next input`() {
        assertNotEquals(
            fingerprint.copy(jvmArgs = listOf("-Da", "b")).digest(),
            fingerprint.copy(jvmArgs = listOf("-Da b")).digest(),
        )
        assertNotEquals(
            fingerprint.copy(includeTags = setOf("x"), excludeTags = emptySet()).digest(),
            fingerprint.copy(includeTags = emptySet(), excludeTags = setOf("x")).digest(),
        )
    }

    @Test
    fun `equal inputs give the same key whatever the map order`() =
        assertEquals(key(), key(reached = linkedMapOf("a.Bar" to "m2", "a.Foo" to "m1")))

    @Test
    fun `every key input changes the key`() {
        val changed =
            listOf(
                key(id = id.copy(ordinal = 1)),
                key(bytes = byteArrayOf(1, 3)),
                key(tests = mapOf("a.FooTest" to "t2", "a.FooTest\$Nested" to "n1")),
                key(tests = mapOf("a.FooTest" to "t1", "a.FooTest\$Nested" to "n2")),
                key(tests = mapOf("a.FooTest" to "t1")),
                key(reached = mapOf("a.Foo" to "m1", "a.Bar" to "m3")),
                key(reached = mapOf("a.Foo" to "m1", "a.Bar" to "m2", "a.Baz" to "m4")),
            )

        assertEquals(changed.size + 1, (changed + key()).toSet().size)
    }

    @Test
    fun `a class counts as a test or a main class, not either`() =
        assertNotEquals(
            key(tests = mapOf("a.X" to "x"), reached = emptyMap()),
            key(tests = emptyMap(), reached = mapOf("a.X" to "x")),
        )
}
