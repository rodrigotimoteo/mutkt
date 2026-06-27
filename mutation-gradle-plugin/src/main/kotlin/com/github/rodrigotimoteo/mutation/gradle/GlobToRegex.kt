package com.github.rodrigotimoteo.mutation.gradle

/**
 * Convert a glob pattern to a regex string matched against a slashed
 * (segment-separated) path. Supports:
 *
 * - `**` (zero or more path segments; bare `**` matches any chars)
 * - `*` (zero or more chars within a single segment, does not cross `/`)
 * - `?` (one char within a segment)
 * - A double-star followed by slash and a simple class name emits a
 *   non-capturing group with an optional trailing slash so the trailing
 *   simple class name matches with or without a package prefix.
 *
 * The returned pattern is anchored with `^` and `$`; callers that need
 * an unanchored body should re-build or substring-strip the anchors.
 */
internal fun globToRegex(glob: String): String {
    val regex = StringBuilder("^")
    var i = 0
    while (i < glob.length) {
        val c = glob[i]
        when {
            c == '*' && i + 1 < glob.length && glob[i + 1] == '*' -> {
                i += 2
                if (i < glob.length && glob[i] == '/') {
                    i++
                    if (i == glob.length) {
                        regex.append(".*")
                    } else {
                        regex.append("(?:.*/)?")
                    }
                } else {
                    regex.append(".*")
                }
            }
            c == '*' -> {
                regex.append("[^/]*")
                i++
            }
            c == '?' -> {
                regex.append("[^/]")
                i++
            }
            else -> {
                regex.append(Regex.escape(c.toString()))
                i++
            }
        }
    }
    regex.append("$")
    return regex.toString()
}
