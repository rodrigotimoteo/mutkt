package com.github.rodrigotimoteo.mutkt.application.port

/** Finds source text for reports. */
public interface SourceLookup {
    /**
     * The text of [line] (1-based) in [sourceFile], the compiled `SourceFile` name of [className], or null when
     * unavailable. Never guesses a file from the class name.
     */
    public fun lineText(
        className: String,
        sourceFile: String?,
        line: Int,
    ): String?
}
