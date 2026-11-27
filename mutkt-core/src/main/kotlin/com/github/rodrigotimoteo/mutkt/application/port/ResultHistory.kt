package com.github.rodrigotimoteo.mutkt.application.port

/** Keeps the results of the last run, so the next run reuses those of unchanged mutants. */
public interface ResultHistory {
    /** The stored history, null when there is none. */
    public fun read(): History?

    /** Replaces the stored history. */
    public fun write(history: History)
}
