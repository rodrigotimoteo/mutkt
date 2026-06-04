package com.github.rodrigotimoteo.mutation.metadata

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

/**
 * Parses Kotlin @Metadata annotation from class bytecode.
 * Used to detect Kotlin-specific constructs and synthetic methods.
 */
object KotlinMetadataParser {
    data class KotlinClassInfo(
        val className: String,
        val isKotlinClass: Boolean,
        val isDataClass: Boolean = false,
        val isSealedClass: Boolean = false,
        val isInlineClass: Boolean = false,
        val isObject: Boolean = false,
        val isInterface: Boolean = false,
        val isEnumClass: Boolean = false,
    )

    fun parse(classBytes: ByteArray): KotlinClassInfo {
        val reader = ClassReader(classBytes)
        val visitor = MetadataVisitor()
        reader.accept(visitor, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        return visitor.classInfo
    }

    fun isKotlinSyntheticMethod(name: String): Boolean {
        return name.contains("\$") && (
            name.contains("copy\$default") ||
                name.contains("component") ||
                name.contains("\$serializer") ||
                name.contains("<init>\$default") ||
                name.contains("toString") ||
                name.contains("hashCode") ||
                name.contains("equals")
        )
    }

    private class MetadataVisitor : ClassVisitor(Opcodes.ASM9) {
        var classInfo = KotlinClassInfo(className = "", isKotlinClass = false)
        private var metadataFound = false

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?,
        ) {
            val className = name.replace('/', '.')
            val isKotlin = false // Will be set in visitAnnotation

            val isData = (access and Opcodes.ACC_FINAL) != 0 // Heuristic
            val isInterface = (access and Opcodes.ACC_INTERFACE) != 0

            classInfo =
                classInfo.copy(
                    className = className,
                    isInterface = isInterface,
                    isDataClass = isData,
                )
            super.visit(version, access, name, signature, superName, interfaces)
        }

        override fun visitAnnotation(
            desc: String,
            visible: Boolean,
        ): org.objectweb.asm.AnnotationVisitor? {
            if (desc == "Lkotlin/Metadata;") {
                metadataFound = true
                classInfo = classInfo.copy(isKotlinClass = true)
            }
            return super.visitAnnotation(desc, visible)
        }
    }
}
