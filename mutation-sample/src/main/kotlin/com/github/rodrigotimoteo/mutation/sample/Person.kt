package com.github.rodrigotimoteo.mutation.sample

/**
 * Data class representing a person.
 * Tests data class copy() mutation and null safety.
 */
data class Person(
    val name: String,
    val age: Int,
    val email: String?,
) {
    fun isAdult(): Boolean = age >= 18

    fun displayName(): String {
        val emailSuffix = email?.let { " ($it)" } ?: ""
        return "$name$emailSuffix"
    }

    fun withAge(newAge: Int): Person = copy(age = newAge)
}

/**
 * Creates a list of persons from names.
 */
fun createPersons(names: List<String>): List<Person> {
    return names.mapIndexed { index, name ->
        Person(name = name, age = 18 + index, email = null)
    }
}

/**
 * Filters adult persons.
 */
fun filterAdults(persons: List<Person>): List<Person> {
    return persons.filter { it.isAdult() }
}
