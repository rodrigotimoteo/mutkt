package com.github.rodrigotimoteo.mutation.metadata

import com.github.rodrigotimoteo.mutation.mutator.isKotlinSyntheticMethod
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
        val isInterface: Boolean = false,
    )

    fun parse(classBytes: ByteArray): KotlinClassInfo {
        val reader = ClassReader(classBytes)
        val visitor = MetadataVisitor()
        reader.accept(visitor, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        return visitor.classInfo
    }

    @Deprecated(
        message = "Moved to com.github.rodrigotimoteo.mutation.mutator.isKotlinSyntheticMethod",
        replaceWith =
            ReplaceWith(
                expression = "isKotlinSyntheticMethod(name)",
                imports = ["com.github.rodrigotimoteo.mutation.mutator.isKotlinSyntheticMethod"],
            ),
    )
    fun isKotlinSyntheticMethod(name: String): Boolean = com.github.rodrigotimoteo.mutation.mutator.isKotlinSyntheticMethod(name)

    private class MetadataVisitor : ClassVisitor(Opcodes.ASM9) {
        var classInfo = KotlinClassInfo(className = "", isKotlinClass = false)

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
            classInfo = KotlinClassInfo(className = className, isKotlinClass = false, isInterface = isInterface)
            super.visit(version, access, name, signature, superName, interfaces)
        }

        override fun visitAnnotation(
            desc: String,
            visible: Boolean,
        ): org.objectweb.asm.AnnotationVisitor? {
            if (desc == "Lkotlin/Metadata;") {
                classInfo = classInfo.copy(isKotlinClass = true)
            }
            return super.visitAnnotation(desc, visible)
        }
    }
}
