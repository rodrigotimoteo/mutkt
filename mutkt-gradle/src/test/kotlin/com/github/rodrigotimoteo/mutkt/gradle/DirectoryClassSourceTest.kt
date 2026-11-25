package com.github.rodrigotimoteo.mutkt.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DirectoryClassSourceTest {
    @TempDir
    lateinit var dir: File

    private fun touch(path: String) = dir.resolve(path).apply { parentFile.mkdirs() }.writeBytes(byteArrayOf(1))

    @Test
    fun `names classes by relative path and skips module-info, META-INF and other files`() {
        touch("a/b/Foo.class")
        touch("a/b/Foo${'$'}Inner.class")
        touch("Top.class")
        touch("module-info.class")
        touch("META-INF/versions/9/a/b/Foo.class")
        touch("META-INF/MANIFEST.MF")
        touch("a/b/readme.txt")

        val names = DirectoryClassSource(dir.toPath()).classes().map { it.name }.sorted()

        assertEquals(listOf("Top", "a.b.Foo", "a.b.Foo${'$'}Inner"), names)
    }
}
