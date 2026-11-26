package com.github.rodrigotimoteo.mutkt.asm

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter

private const val MAX_DEPTH = 1000

/**
 * Class bytes by internal name for every non-JDK type the class under mutation can reference: project, test
 * and dependency classes (Kotlin stdlib included), i.e. the test runtime classpath, then the main compile classpath
 * for `compileOnly` types. Null for unknown types.
 */
internal typealias Hierarchy = (String) -> ByteArray?

/** Computes frames without loading classes: supertypes come from [hierarchy], JDK types from the platform loader. */
internal class HierarchyClassWriter(
    private val hierarchy: Hierarchy,
) : ClassWriter(COMPUTE_FRAMES) {
    // An interface's super is java/lang/Object, so merges involving one yield Object, which the verifier
    // accepts because it treats interfaces like Object.
    public override fun getCommonSuperClass(
        type1: String,
        type2: String,
    ): String {
        val ancestors = chain(type1).toSet()
        return chain(type2).first { it in ancestors }
    }

    private fun chain(type: String): Sequence<String> =
        generateSequence(type to 0) { (current, depth) ->
            check(depth < MAX_DEPTH) { "Cyclic hierarchy at $type" }
            superOf(current)?.let { it to depth + 1 }
        }.map { it.first }

    private fun superOf(type: String): String? = ClassReader(bytesOf(type)).superName

    private fun bytesOf(type: String): ByteArray = hierarchy.bytesOrNull(type) ?: throw UnresolvedTypeException(type)
}

/** A type that frame computation needs but neither the hierarchy nor the JDK knows. */
internal class UnresolvedTypeException(
    type: String,
) : IllegalStateException("cannot resolve ${type.replace('/', '.')} to compute stack frames")

/** The analysed [classes] (bytes by binary name) first, then [classpath] (by internal name). */
internal fun hierarchyOf(
    classes: Map<String, ByteArray>,
    classpath: (String) -> ByteArray?,
): Hierarchy = { type -> classes[type.replace('/', '.')] ?: classpath(type) }

/** A hierarchy that knows no class, so only JDK types resolve. */
internal val NoClasses: Hierarchy = { null }

/** Bytes of [type] from the hierarchy, or else from the JDK; null when neither knows it. */
internal fun Hierarchy.bytesOrNull(type: String): ByteArray? =
    invoke(type) ?: ClassLoader.getPlatformClassLoader().getResourceAsStream("$type.class")?.use { it.readBytes() }
