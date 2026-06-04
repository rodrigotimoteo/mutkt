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
        // Only match known compiler-generated patterns
        return name == "copy\$default" ||
            name.startsWith("component") && name.endsWith("\$default") ||
            name.endsWith("\$serializer") ||
            name == "<init>\$default" ||
            name == "toString\$default" ||
            name == "hashCode\$default" ||
            name == "equals\$default"
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
            val isInterface = (access and Opcodes.ACC_INTERFACE) != 0

            // isDataClass requires proper metadata parsing
            classInfo =
                classInfo.copy(
                    className = className,
                    isInterface = isInterface,
                    isDataClass = false,
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
