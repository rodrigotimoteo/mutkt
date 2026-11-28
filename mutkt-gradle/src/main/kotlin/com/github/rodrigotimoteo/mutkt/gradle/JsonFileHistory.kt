package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.application.historyFromJson
import com.github.rodrigotimoteo.mutkt.application.port.History
import com.github.rodrigotimoteo.mutkt.application.port.ResultHistory
import com.github.rodrigotimoteo.mutkt.application.toJson
import groovy.json.JsonException
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * The result history as a JSON [file], next to the digests of the files the run hashed; a file that cannot be read
 * fails the run rather than being dropped.
 */
internal class JsonFileHistory(
    private val file: File,
) : ResultHistory {
    private val stored by lazy { load() }

    /** Digests of the files hashed for the fingerprint, reused from the last run and stored for the next. */
    val digests: FileDigests by lazy { FileDigests(stored.second) }

    override fun read(): History? = stored.first

    // Written aside and moved in one step, so a build stopped mid-write leaves the previous history.
    override fun write(history: History) {
        val dir =
            file.absoluteFile.parentFile
                .toPath()
                .also { Files.createDirectories(it) }
        val next = Files.createTempFile(dir, file.name, ".tmp")
        val files =
            digests.used().mapValues { (_, it) ->
                mapOf(
                    SIZE to it.size,
                    MODIFIED to it.modified,
                    DIGEST to it.digest,
                )
            }
        Files.writeString(next, JsonOutput.toJson(history.toJson() + (FILES to files)))
        Files.move(next, file.toPath(), REPLACE_EXISTING, ATOMIC_MOVE)
    }

    private fun load(): Pair<History?, Map<String, StampedDigest>> {
        if (!file.exists()) return null to emptyMap()
        val text = text()
        return try {
            parse(text)
        } catch (e: JsonException) {
            throw failure(CORRUPT, e.message, e)
        } catch (e: IllegalArgumentException) {
            // Also JsonSlurper's answer to an empty file.
            throw failure(CORRUPT, e.message, e)
        }
    }

    private fun text(): String =
        try {
            file.readText()
        } catch (e: IOException) {
            throw failure("cannot be read", e.message, e)
        }

    private fun failure(
        problem: String,
        detail: String?,
        cause: Throwable?,
    ) = GradleException(
        "The MutKt result history ${file.path} $problem (${detail?.lines()?.first()}): delete it and run again",
        cause,
    )
}

private fun parse(text: String): Pair<History?, Map<String, StampedDigest>> {
    val tree = JsonSlurper().parseText(text)
    require(tree is Map<*, *>) { "not a JSON object" }
    // The digests of another format version's run are dropped with its results.
    val history = historyFromJson(tree) ?: return null to emptyMap()
    return history to stamps(tree[FILES])
}

private fun stamps(files: Any?): Map<String, StampedDigest> {
    if (files == null) return emptyMap()
    require(files is Map<*, *>) { "files is not an object" }
    return files.entries.associate { (path, entry) ->
        val fields = entry as? Map<*, *> ?: emptyMap<Any, Any>()
        val size = fields[SIZE] as? Number
        val modified = fields[MODIFIED] as? Number
        val digest = fields[DIGEST] as? String
        require(size != null && modified != null && digest != null) { "file entry $path lacks a size, time or digest" }
        path.toString() to StampedDigest(size.toLong(), modified.toLong(), digest)
    }
}

private const val CORRUPT = "is corrupt"
private const val FILES = "files"
private const val SIZE = "size"
private const val MODIFIED = "modified"
private const val DIGEST = "digest"
