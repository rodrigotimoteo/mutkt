package com.github.rodrigotimoteo.mutation.gradle

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GlobToRegexTest {
    private fun matches(
        pattern: String,
        input: String,
    ): Boolean = Regex(pattern).matches(input)

    @Test
    fun `single ? matches exactly one non-slash character`() {
        val regex = globToRegex("a?c")
        // `Regex.escape` wraps each plain char in \Q...\E; the ? expands
        // to a character class that does not cross `/`.
        assertThat(regex).contains("[^/]")
        assertThat(matches(regex, "abc")).isTrue
        assertThat(matches(regex, "axc")).isTrue
    }

    @Test
    fun `? in the middle of a segment does not cross slashes`() {
        val regex = globToRegex("a/b?c/d")
        assertThat(matches(regex, "a/bxc/d")).isTrue
        assertThat(matches(regex, "a/b/d")).isFalse
        assertThat(matches(regex, "a/bx/c/d")).isFalse
    }

    @Test
    fun `multiple ? wildcards each match exactly one character`() {
        val regex = globToRegex("???")
        assertThat(matches(regex, "abc")).isTrue
        assertThat(matches(regex, "ab")).isFalse
        assertThat(matches(regex, "abcd")).isFalse
    }

    @Test
    fun `trailing doublestar with no slash matches any trailing characters`() {
        // `**` alone (no following slash) — the trailing `.*` branch in
        // globToRegex handles this by appending `.*` directly.
        val regex = globToRegex("com/example/**")
        assertThat(regex).endsWith(".*$")
        assertThat(matches(regex, "com/example/")).isTrue
        assertThat(matches(regex, "com/example/Foo")).isTrue
        assertThat(matches(regex, "com/example/sub/Foo")).isTrue
    }

    @Test
    fun `trailing doublestar followed by slash matches any path under the prefix`() {
        // `**/` at the end of a glob — the `i == glob.length` branch
        // emits `.*` so the trailing `/**` matches any path.
        val regex = globToRegex("com/example/**/")
        assertThat(regex).endsWith(".*$")
        assertThat(matches(regex, "com/example/")).isTrue
        assertThat(matches(regex, "com/example/Foo")).isTrue
        assertThat(matches(regex, "com/example/sub/Foo")).isTrue
    }

    @Test
    fun `doublestar with simple class name matches with or without package prefix`() {
        val regex = globToRegex("**/BuildConfig")
        // The branch emits `(?:.*/)?` for `**/BuildConfig` so the
        // simple class name can appear bare or under any package.
        assertThat(regex).contains("(?:.*/)?")
        assertThat(matches(regex, "BuildConfig")).isTrue
        assertThat(matches(regex, "com/BuildConfig")).isTrue
        assertThat(matches(regex, "com/example/BuildConfig")).isTrue
        assertThat(matches(regex, "BuildConfigExtra")).isFalse
    }

    @Test
    fun `single star does not cross slashes`() {
        val regex = globToRegex("a/*/c")
        assertThat(matches(regex, "a/b/c")).isTrue
        assertThat(matches(regex, "a/bb/c")).isTrue
        assertThat(matches(regex, "a/b/c/d")).isFalse
    }

    @Test
    fun `special characters are escaped with Regex-escape brackets`() {
        val regex = globToRegex("a.b+c")
        // `Regex.escape(".")` and `Regex.escape("+")` produce \Q.\E and
        // \Q+\E blocks respectively, rather than literal `\.` / `\+`.
        assertThat(regex).contains("\\Q.\\E")
        assertThat(regex).contains("\\Q+\\E")
        assertThat(matches(regex, "a.b+c")).isTrue
        assertThat(matches(regex, "aXb+c")).isFalse
    }
}
