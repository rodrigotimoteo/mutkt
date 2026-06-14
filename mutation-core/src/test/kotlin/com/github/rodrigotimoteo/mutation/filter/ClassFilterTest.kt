package com.github.rodrigotimoteo.mutation.filter

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClassFilterTest {
    @Test
    fun `shouldMutate returns true when no patterns configured`() {
        val filter = ClassFilter.all()
        assertTrue(filter.shouldMutate("com.example.MyClass"), "ClassFilter.all() should permit any class")
        assertTrue(filter.shouldMutate("org.anything.Bar"), "ClassFilter.all() should permit any class")
    }

    @Test
    fun `shouldMutate returns true when className matches includePattern`() {
        val filter = ClassFilter.fromPatterns(targetClasses = listOf("com\\.example\\..*"))
        assertTrue(filter.shouldMutate("com.example.MyClass"))
        assertTrue(filter.shouldMutate("com.example.sub.SubClass"))
    }

    @Test
    fun `shouldMutate returns false when className does not match includePattern`() {
        val filter = ClassFilter.fromPatterns(targetClasses = listOf("com\\.example\\..*"))
        assertFalse(filter.shouldMutate("org.other.MyClass"))
    }

    @Test
    fun `shouldMutate returns false when className matches excludePattern`() {
        val filter = ClassFilter.fromPatterns(excludeClasses = listOf("com\\.example\\..*"))
        assertFalse(filter.shouldMutate("com.example.MyClass"))
    }

    @Test
    fun `exclude overrides include`() {
        val filter =
            ClassFilter.fromPatterns(
                targetClasses = listOf("com\\.example\\..*"),
                excludeClasses = listOf("com\\.example\\.internal\\..*"),
            )
        assertTrue(filter.shouldMutate("com.example.MyClass"), "should be permitted by include")
        assertFalse(filter.shouldMutate("com.example.internal.Hidden"), "should be blocked by exclude")
    }

    @Test
    fun `multiple includePatterns any match returns true`() {
        val filter =
            ClassFilter.fromPatterns(
                targetClasses = listOf("com\\.example\\.moduleA\\..*", "com\\.example\\.moduleB\\..*"),
            )
        assertTrue(filter.shouldMutate("com.example.moduleA.Foo"))
        assertTrue(filter.shouldMutate("com.example.moduleB.Bar"))
        assertFalse(filter.shouldMutate("com.example.moduleC.Baz"))
    }

    @Test
    fun `multiple excludePatterns any match returns false`() {
        val filter =
            ClassFilter.fromPatterns(
                excludeClasses = listOf(".*\\.SomeGenerated", ".*MyTest", "com\\.example\\.internal\\..*"),
            )
        assertFalse(filter.shouldMutate("com.example.SomeGenerated"))
        assertFalse(filter.shouldMutate("com.example.MyTest"))
        assertFalse(filter.shouldMutate("com.example.internal.Utils"))
        assertTrue(filter.shouldMutate("com.example.Foo"))
    }

    @Test
    fun `regex patterns handle complex class names`() {
        val filter =
            ClassFilter.fromPatterns(
                targetClasses = listOf("com\\.example\\.\\w+Service\$"),
            )
        assertTrue(filter.shouldMutate("com.example.UserService"))
        assertTrue(filter.shouldMutate("com.example.OrderService"))
        assertFalse(filter.shouldMutate("com.example.UserRepository"))
    }

    @Test
    fun `shouldRunTest returns true with no test patterns`() {
        val filter = ClassFilter.all()
        assertTrue(filter.shouldRunTest("com.example.MyClassTest"))
    }

    @Test
    fun `shouldRunTest returns true when test class matches targetTestPattern`() {
        val filter =
            ClassFilter.fromPatterns(
                targetTests = listOf("com\\.example\\.MyClassTest"),
            )
        assertTrue(filter.shouldRunTest("com.example.MyClassTest"))
        assertFalse(filter.shouldRunTest("com.example.OtherTest"))
    }

    @Test
    fun `shouldRunTest returns false when test class matches excludeTestPattern`() {
        val filter =
            ClassFilter.fromPatterns(
                excludeTests = listOf(".*IntegrationTest"),
            )
        assertFalse(filter.shouldRunTest("com.example.IntegrationTest"))
        assertTrue(filter.shouldRunTest("com.example.MyClassTest"))
    }

    @Test
    fun `fromPatterns builds correctly from lists`() {
        val filter =
            ClassFilter.fromPatterns(
                targetClasses = listOf("com\\.example\\..*"),
                excludeClasses = listOf("com\\.example\\.generated\\..*"),
                targetTests = listOf("com\\.example\\..*Test"),
                excludeTests = listOf(".*SlowTest"),
            )
        assertTrue(filter.shouldMutate("com.example.MyClass"))
        assertFalse(filter.shouldMutate("com.example.generated.AutoClass"))
        assertTrue(filter.shouldRunTest("com.example.MyTest"))
        assertFalse(filter.shouldRunTest("com.example.SlowTest"))
    }

    @Test
    fun `all returns permissive filter`() {
        val filter = ClassFilter.all()
        assertTrue(filter.shouldMutate("anything.at.all"))
        assertTrue(filter.shouldRunTest("anything.at.all.Test"))
    }
}
