package com.github.rodrigotimoteo.mutation.gradle

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GeneratedClassFilterTest {
    @Test
    fun `exact match - bare pattern matches only that class name`() {
        val patterns = setOf("R")
        assertThat(GeneratedClassFilter.shouldExclude("R", patterns)).isTrue()
        assertThat(GeneratedClassFilter.shouldExclude("Rx", patterns)).isFalse()
        assertThat(GeneratedClassFilter.shouldExclude("MyR", patterns)).isFalse()
    }

    @Test
    fun `prefix match - BuildConfig matches only BuildConfig exactly`() {
        val patterns = setOf("BuildConfig")
        assertThat(GeneratedClassFilter.shouldExclude("BuildConfig", patterns)).isTrue()
        assertThat(GeneratedClassFilter.shouldExclude("MyBuildConfig", patterns)).isFalse()
        assertThat(GeneratedClassFilter.shouldExclude("BuildConfigExt", patterns)).isFalse()
    }

    @Test
    fun `suffix match - _Impl pattern matches class names ending in _Impl`() {
        val patterns = setOf("*_Impl")
        assertThat(GeneratedClassFilter.shouldExclude("MyClass_Impl", patterns)).isTrue()
        assertThat(GeneratedClassFilter.shouldExclude("Database_Impl", patterns)).isTrue()
        assertThat(GeneratedClassFilter.shouldExclude("Impl", patterns)).isFalse()
    }

    @Test
    fun `middle match - Hilt wildcard matches names containing Hilt`() {
        val patterns = setOf("*Hilt*")
        assertThat(GeneratedClassFilter.shouldExclude("MyClassHiltModule", patterns)).isTrue()
        assertThat(GeneratedClassFilter.shouldExclude("HiltApp", patterns)).isTrue()
        assertThat(GeneratedClassFilter.shouldExclude("MyModule", patterns)).isFalse()
    }

    @Test
    fun `dollar suffix pattern matches nested class names starting with R dollar`() {
        val patterns = setOf("R\$*")
        assertThat(GeneratedClassFilter.shouldExclude("R\$drawable", patterns)).isTrue()
        assertThat(GeneratedClassFilter.shouldExclude("R\$layout", patterns)).isTrue()
        assertThat(GeneratedClassFilter.shouldExclude("R\$id", patterns)).isTrue()
        assertThat(GeneratedClassFilter.shouldExclude("R", patterns)).isFalse()
    }

    @Test
    fun `dollar Lambda pattern matches Kotlin synthetic lambda classes`() {
        val patterns = setOf("\$\$Lambda\$*")
        assertThat(GeneratedClassFilter.shouldExclude("\$\$Lambda\$1234", patterns)).isTrue()
        assertThat(GeneratedClassFilter.shouldExclude("Outer\$\$Lambda\$7", patterns)).isTrue()
        assertThat(GeneratedClassFilter.shouldExclude("Lambda", patterns)).isFalse()
    }

    @Test
    fun `plain star prefix pattern matches anything with the suffix`() {
        val patterns = setOf("*ComposableSingletons*")
        assertThat(GeneratedClassFilter.shouldExclude("ComposableSingletons", patterns)).isTrue()
        assertThat(GeneratedClassFilter.shouldExclude("ComposableSingletons\$MainActivity", patterns)).isTrue()
        assertThat(GeneratedClassFilter.shouldExclude("MyComposableSingletons\$X", patterns)).isTrue()
        assertThat(GeneratedClassFilter.shouldExclude("MyClass", patterns)).isFalse()
    }

    @Test
    fun `star suffix pattern matches anything with the prefix`() {
        val patterns = setOf("Hilt_*")
        assertThat(GeneratedClassFilter.shouldExclude("Hilt_MyApp", patterns)).isTrue()
        assertThat(GeneratedClassFilter.shouldExclude("Hilt_", patterns)).isTrue()
        assertThat(GeneratedClassFilter.shouldExclude("MyHilt_MyApp", patterns)).isFalse()
    }

    @Test
    fun `empty pattern set excludes nothing`() {
        assertThat(GeneratedClassFilter.shouldExclude("R", emptySet())).isFalse()
        assertThat(GeneratedClassFilter.shouldExclude("MyClass", emptySet())).isFalse()
    }

    @Test
    fun `multi-pattern filter excludes when any pattern matches`() {
        val patterns = setOf("R\$*", "*_Impl", "BuildConfig")
        assertThat(GeneratedClassFilter.shouldExclude("R\$drawable", patterns)).isTrue()
        assertThat(GeneratedClassFilter.shouldExclude("MyClass_Impl", patterns)).isTrue()
        assertThat(GeneratedClassFilter.shouldExclude("BuildConfig", patterns)).isTrue()
        assertThat(GeneratedClassFilter.shouldExclude("MyRepository", patterns)).isFalse()
    }

    @Test
    fun `ordinary business class names are not excluded`() {
        val patterns = setOf("R\$*", "*_Impl", "BuildConfig", "Hilt_*", "*ComposableSingletons*")
        assertThat(GeneratedClassFilter.shouldExclude("MyClass", patterns)).isFalse()
        assertThat(GeneratedClassFilter.shouldExclude("MyRepository", patterns)).isFalse()
        assertThat(GeneratedClassFilter.shouldExclude("MyViewModel", patterns)).isFalse()
        assertThat(GeneratedClassFilter.shouldExclude("MyDto", patterns)).isFalse()
        assertThat(GeneratedClassFilter.shouldExclude("MyMapper", patterns)).isFalse()
    }
}
