package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.application.port.History
import com.github.rodrigotimoteo.mutkt.application.port.HistoryEntry
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus

/** Version of the history file format; a file of another version is ignored. */
internal const val HISTORY_FORMAT_VERSION: Int = 1

/**
 * The history file as a JSON tree of maps, lists, strings and numbers, for any JSON writer:
 * `{"version": 1, "fingerprint": "…", "mutants": [{"id": "…", "key": "…", "status": "KILLED", "killedBy": "…"}]}`,
 * where killedBy is absent unless set.
 */
public fun History.toJson(): Map<String, Any?> =
    mapOf(
        VERSION to HISTORY_FORMAT_VERSION,
        FINGERPRINT to fingerprint,
        MUTANTS to
            entries.map { entry ->
                mapOf(ID to entry.mutant, KEY to entry.key, STATUS to entry.status.name) +
                    listOfNotNull(entry.killedBy?.let { KILLED_BY to it })
            },
    )

/**
 * The history in a parsed [tree], null when it has another format version; any other shape fails with an
 * IllegalArgumentException whose message starts with "Corrupt history".
 */
public fun historyFromJson(tree: Map<*, *>): History? =
    try {
        parse(tree)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Corrupt history: ${e.message}", e)
    }

private fun parse(tree: Map<*, *>): History? {
    val version = (tree[VERSION] as? Number)?.toDouble()
    require(version != null && version % 1.0 == 0.0) { "format version is missing or not a whole number" }
    if (version != HISTORY_FORMAT_VERSION.toDouble()) return null
    val mutants = tree[MUTANTS]
    require(mutants is List<*>) { "no mutant list" }
    return History(tree.string(FINGERPRINT), mutants.map(::entry))
}

private fun entry(item: Any?): HistoryEntry {
    require(item is Map<*, *>) { "entry is not an object: $item" }
    val killedBy = item[KILLED_BY]
    require(killedBy == null || killedBy is String) { "entry has a non-text killedBy: $item" }
    return HistoryEntry(item.string(ID), item.string(KEY), MutantStatus.valueOf(item.string(STATUS)), killedBy)
}

private fun Map<*, *>.string(name: String): String =
    this[name] as? String ?: throw IllegalArgumentException("field $name is missing or not text in $this")

private const val VERSION = "version"
private const val FINGERPRINT = "fingerprint"
private const val MUTANTS = "mutants"
private const val ID = "id"
private const val KEY = "key"
private const val STATUS = "status"
private const val KILLED_BY = "killedBy"
