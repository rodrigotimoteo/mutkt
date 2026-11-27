package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.junit.fork.writeClassFiles
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
): Path = writeClassFiles(target, classes)

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
    return writeJar(target, compileKotlin(source), mapOf("Premain-Class" to "StallAgent"))
}

/** Writes [classes] (bytes by binary name) as a jar at [target] with [attributes] in its manifest. */
internal fun writeJar(
    target: Path,
    classes: Map<String, ByteArray>,
    attributes: Map<String, String> = emptyMap(),
): Path {
    val manifest = Manifest().apply { mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0" }
    attributes.forEach(manifest.mainAttributes::putValue)
    JarOutputStream(Files.newOutputStream(target), manifest).use { jar ->
        classes.forEach { (name, bytes) ->
            jar.putNextEntry(JarEntry(name.replace('.', '/') + ".class"))
            jar.write(bytes)
            jar.closeEntry()
        }
    }
    return target
}
