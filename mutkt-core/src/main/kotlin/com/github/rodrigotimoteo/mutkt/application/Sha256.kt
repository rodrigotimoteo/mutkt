package com.github.rodrigotimoteo.mutkt.application

import java.security.MessageDigest

/** Hex SHA-256 of [bytes]. */
internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance(SHA_256).digest(bytes).toHex()

/** Hex SHA-256 of [parts]; each is length-prefixed so no value can run into the next one. */
internal fun sha256(parts: List<String>): String {
    val digest = MessageDigest.getInstance(SHA_256)
    for (part in parts) {
        val bytes = part.toByteArray()
        digest.update("${bytes.size}:".toByteArray())
        digest.update(bytes)
    }
    return digest.digest().toHex()
}

/** The list preceded by its size, so it cannot run into the next input. */
internal fun List<String>.counted(): List<String> = listOf(size.toString()) + this

/** Keys and values in iteration order, counted. */
internal fun Map<String, String>.parts(): List<String> = entries.flatMap { listOf(it.key, it.value) }.counted()

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private const val SHA_256 = "SHA-256"
