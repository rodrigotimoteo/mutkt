package com.github.rodrigotimoteo.mutkt.application

/** Class-name pattern over dotted names: `*` stays in one segment, `**` crosses dots, `?` is one non-dot character. */
public data class Glob(
    val pattern: String,
) {
    private val regex = Regex(pattern.toRegexSource())

    init {
        require(pattern.isNotBlank()) { "Glob pattern must not be blank" }
    }

    /** True when the whole [name] matches. */
    public fun matches(name: String): Boolean = regex.matches(name)
}

// Anything but the three wildcards is escaped, so '$' and '+' in class names stay literal.
private fun String.toRegexSource(): String =
    split("**").joinToString(".*") { part ->
        part.split("*").joinToString("[^.]*") { literal ->
            literal.split("?").joinToString("[^.]") { Regex.escape(it) }
        }
    }
