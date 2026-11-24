package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.api.provider.Property

/** The `reports { }` block of the `mutkt` extension. */
public interface ReportsSpec {
    /** Print the summary to the build log. */
    public val console: Property<Boolean>
}
