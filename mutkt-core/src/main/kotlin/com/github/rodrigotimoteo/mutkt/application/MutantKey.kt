package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.domain.MutantId

/**
 * The key a mutant's result is reused under: its [id] and [bytes], and the digests, by class name, of the covering
 * [tests] (nested classes included) and of every main class they [reached]. A whole-suite mutant passes them all.
 */
internal fun mutantKey(
    id: MutantId,
    bytes: ByteArray,
    tests: Map<String, String>,
    reached: Map<String, String>,
): String = sha256(listOf(id.toString(), sha256(bytes)) + tests.toSortedMap().parts() + reached.toSortedMap().parts())
