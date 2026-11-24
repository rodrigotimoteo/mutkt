package com.github.rodrigotimoteo.mutkt.testing

import java.nio.file.Path
import java.nio.file.Paths

/** Jar or class directory that defines [type]; fails for classes without a code source (JDK, generated). */
fun codeSourceOf(type: Class<*>): Path {
    val location = checkNotNull(type.protectionDomain.codeSource?.location) { "No code source for ${type.name}" }
    return Paths.get(location.toURI())
}
