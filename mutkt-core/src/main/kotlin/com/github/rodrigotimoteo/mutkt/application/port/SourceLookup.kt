package com.github.rodrigotimoteo.mutkt.application.port

/** Finds source text for reports. */
public interface SourceLookup {
    /** The text of [line] (1-based) in the source of [className], or null when unavailable. */
    public fun lineText(
        className: String,
        line: Int,
    ): String?
}
