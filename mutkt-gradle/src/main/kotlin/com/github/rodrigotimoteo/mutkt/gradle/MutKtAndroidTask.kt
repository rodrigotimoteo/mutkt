package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Internal

/** `mutkt` on Android: AGP's `toGet` fills these two lists, which [classesDirs] reads. */
internal abstract class MutKtAndroidTask : MutKtTask() {
    // Tracked through classesDirs.
    @get:Internal
    abstract val classJars: ListProperty<RegularFile>

    @get:Internal
    abstract val classDirectories: ListProperty<Directory>
}
