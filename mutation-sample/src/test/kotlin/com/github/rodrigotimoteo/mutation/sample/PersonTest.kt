package com.github.rodrigotimoteo.mutation.sample

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PersonTest {

    @Nested
    inner class CreationTests {
        @Test
        fun `create person with all fields`() {
            val person = Person("Alice", 25, "alice@example.com")
            assertEquals("Alice", person.name)
            assertEquals(25, person.age)
            assertEquals("alice@example.com", person.email)
        }

        @Test
        fun `create person without email`() {
            val person = Person("Bob", 30, null)
            assertNull(person.email)
        }
    }

    @Nested
    inner class BehaviorTests {
        @Test
        fun `isAdult returns true for 18+`() {
            assertTrue(Person("Alice", 18, null).isAdult())
            assertTrue(Person("Bob", 25, null).isAdult())
        }

        @Test
        fun `isAdult returns false for under 18`() {
            assertFalse(Person("Charlie", 17, null).isAdult())
            assertFalse(Person("Diana", 0, null).isAdult())
        }

        @Test
        fun `displayName with email`() {
            val person = Person("Alice", 25, "alice@example.com")
            assertEquals("Alice (alice@example.com)", person.displayName())
        }

        @Test
        fun `displayName without email`() {
            val person = Person("Bob", 30, null)
            assertEquals("Bob", person.displayName())
        }

        @Test
        fun `withAge returns new instance`() {
            val original = Person("Alice", 25, null)
            val updated = original.withAge(26)
            assertEquals(26, updated.age)
            assertEquals("Alice", updated.name)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [18, 21, 30, 65, 100])
    fun `isAdult true for all ages 18+`(age: Int) {
        assertTrue(Person("Test", age, null).isAdult())
    }

    @Nested
    inner class ListOperationsTests {
        @Test
        fun `createPersons generates correct list`() {
            val persons = createPersons(listOf("Alice", "Bob"))
            assertEquals(2, persons.size)
            assertEquals("Alice", persons[0].name)
            assertEquals(18, persons[0].age)
            assertEquals("Bob", persons[1].name)
            assertEquals(19, persons[1].age)
        }

        @Test
        fun `filterAdults filters correctly`() {
            val persons = createPersons(listOf("Alice", "Bob"))
            val adults = filterAdults(persons)
            assertEquals(2, adults.size) // both 18+ so both pass
        }
    }
}
