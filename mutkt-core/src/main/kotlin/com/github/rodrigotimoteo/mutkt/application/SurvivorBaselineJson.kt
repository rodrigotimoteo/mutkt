package com.github.rodrigotimoteo.mutkt.application

/** Version of the survivor baseline format. */
internal const val SURVIVOR_BASELINE_VERSION: Int = 1

/**
 * The baseline as a JSON tree for any JSON writer, sorted so the committed file diffs cleanly:
 * `{"version": 1, "survivors": [{"id": "…", "method": "…"}]}`.
 */
public fun SurvivorBaseline.toJson(): Map<String, Any?> =
    mapOf(
        VERSION to SURVIVOR_BASELINE_VERSION,
        SURVIVORS to
            survivors
                .sortedWith(compareBy(Survivor::id, Survivor::method))
                .map { mapOf(ID to it.id, METHOD to it.method) },
    )

/** The baseline in a parsed [tree]; another shape or version fails with "Corrupt survivor baseline: …". */
public fun survivorBaselineFromJson(tree: Map<*, *>): SurvivorBaseline =
    try {
        parse(tree)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Corrupt survivor baseline: ${e.message}", e)
    }

// A committed file of another version is not dropped like the history: that would turn every survivor into a new one.
private fun parse(tree: Map<*, *>): SurvivorBaseline {
    val version = (tree[VERSION] as? Number)?.toDouble()
    require(version == SURVIVOR_BASELINE_VERSION.toDouble()) {
        "format version ${tree[VERSION]} is not $SURVIVOR_BASELINE_VERSION"
    }
    val survivors = tree[SURVIVORS]
    require(survivors is List<*>) { "no survivor list" }
    return SurvivorBaseline(survivors.mapTo(mutableSetOf(), ::survivor))
}

private fun survivor(item: Any?): Survivor {
    require(item is Map<*, *>) { "entry is not an object: $item" }
    return Survivor(item.string(ID), item.string(METHOD))
}

private fun Map<*, *>.string(name: String): String =
    this[name] as? String ?: throw IllegalArgumentException("field $name is missing or not text in $this")

private const val VERSION = "version"
private const val SURVIVORS = "survivors"
private const val ID = "id"
private const val METHOD = "method"
