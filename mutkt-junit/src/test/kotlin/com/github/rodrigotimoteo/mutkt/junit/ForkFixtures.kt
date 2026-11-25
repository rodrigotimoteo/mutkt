package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

/** Writes [classes] as .class files under [target], the layout a project class directory has. */
internal fun writeClasses(
    target: Path,
    classes: List<ClassFile>,
): Path {
    classes.forEach {
        val file = target.resolve(it.name.replace('.', '/') + ".class")
        Files.createDirectories(file.parent)
        Files.write(file, it.bytes)
    }
    return target
}

/**
 * A java agent for a forked child, started with `-javaagent:<jar>=<arm>`: once the `arm` file exists,
 * the first load of class `Calc` deletes it and hangs.
 * Loading happens before the child reports Started, so this stalls preparation deterministically, exactly once.
 */
internal fun stallAgentJar(target: Path): Path {
    val source =
        """
        import java.lang.instrument.ClassFileTransformer
        import java.lang.instrument.Instrumentation
        import java.security.ProtectionDomain

        object StallAgent {
            @JvmStatic
            fun premain(args: String?, instrumentation: Instrumentation) {
                instrumentation.addTransformer(object : ClassFileTransformer {
                    override fun transform(
                        loader: ClassLoader?, name: String?, redefined: Class<*>?,
                        domain: ProtectionDomain?, bytes: ByteArray?,
                    ): ByteArray? {
                        if (name == "Calc" && java.io.File(args!!).delete()) Thread.sleep(Long.MAX_VALUE)
                        return null
                    }
                })
            }
        }
        """.trimIndent()
    val manifest = Manifest().apply { mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0" }
    manifest.mainAttributes.putValue("Premain-Class", "StallAgent")
    JarOutputStream(Files.newOutputStream(target), manifest).use { jar ->
        compileKotlin(source).forEach { (name, bytes) ->
            jar.putNextEntry(JarEntry(name.replace('.', '/') + ".class"))
            jar.write(bytes)
            jar.closeEntry()
        }
    }
    return target
}
