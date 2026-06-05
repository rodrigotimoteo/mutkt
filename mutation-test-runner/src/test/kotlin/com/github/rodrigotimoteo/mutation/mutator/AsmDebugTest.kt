package com.github.rodrigotimoteo.mutation.mutator

import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import kotlin.test.assertTrue

/**
 * Direct ASM test to verify bytecode contains expected opcodes.
 */
class AsmDebugTest {
    @Test
    fun `verify Calculator bytecode contains mutation points`() {
        val classBytes = loadCalculatorClassBytes()
        assertTrue(classBytes.isNotEmpty(), "Calculator.class is empty")

        val reader = ClassReader(classBytes)
        val methods = mutableListOf<String>()
        val opcodes = mutableListOf<String>()

        reader.accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visit(
                    version: Int,
                    access: Int,
                    name: String,
                    signature: String?,
                    superName: String?,
                    interfaces: Array<out String>?,
                ) {
                    super.visit(version, access, name, signature, superName, interfaces)
                }

                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    val isSynthetic = (access and Opcodes.ACC_SYNTHETIC) != 0
                    val isBridge = (access and Opcodes.ACC_BRIDGE) != 0
                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)

                    if (isSynthetic || isBridge || name.startsWith("<")) {
                        methods.add("SKIP $name$descriptor (syn=$isSynthetic, br=$isBridge)")
                        return mv
                    }

                    methods.add("VISIT $name$descriptor")

                    return object : MethodVisitor(Opcodes.ASM9, mv) {
                        override fun visitInsn(opcode: Int) {
                            opcodes.add("  $name: INS ${getOpcodeName(opcode)} ($opcode)")
                            super.visitInsn(opcode)
                        }

                        override fun visitJumpInsn(
                            opcode: Int,
                            label: Label?,
                        ) {
                            opcodes.add("  $name: JUMP ${getOpcodeName(opcode)} ($opcode)")
                            super.visitJumpInsn(opcode, label)
                        }

                        override fun visitIincInsn(
                            varIndex: Int,
                            increment: Int,
                        ) {
                            opcodes.add("  $name: IINC var=$varIndex inc=$increment")
                            super.visitIincInsn(varIndex, increment)
                        }
                    }
                }
            },
            ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
        )

        assertTrue(methods.isNotEmpty(), "Should visit methods")
        assertTrue(opcodes.isNotEmpty(), "Should find opcodes")
    }

    /**
     * Locate Calculator.class by trying multiple build output paths.
     * Works from both Gradle module dir and IDE project root.
     */
    private fun loadCalculatorClassBytes(): ByteArray {
        val cwd = File(System.getProperty("user.dir"))
        // Walk up to find mutation-sample module if not already there
        val moduleRoot =
            generateSequence(cwd) { it.parentFile }
                .firstOrNull { File(it, "mutation-sample/build/classes/kotlin/main").exists() }
                ?: cwd

        val candidates =
            listOf(
                File(moduleRoot, "mutation-sample/build/classes/kotlin/main"),
                File(moduleRoot, "mutation-sample/build/classes/java/main"),
                File(moduleRoot, "mutation-sample/build/classes"),
            )

        val classesDir =
            candidates.firstOrNull { it.exists() }
                ?: error(
                    "Calculator classes not found. Tried: ${candidates.joinToString { it.path }}\n" +
                        "Working dir: ${cwd.absolutePath}\n" +
                        "Run ':mutation-sample:compileKotlin' first.",
                )

        val classFile = classesDir.resolve("com/github/rodrigotimoteo/mutation/sample/Calculator.class")
        require(classFile.exists()) {
            "Calculator.class not found at $classFile. Run ':mutation-sample:compileKotlin' first."
        }
        return classFile.readBytes()
    }

    private fun getOpcodeName(opcode: Int): String =
        when (opcode) {
            Opcodes.IADD -> "IADD"
            Opcodes.ISUB -> "ISUB"
            Opcodes.IMUL -> "IMUL"
            Opcodes.IDIV -> "IDIV"
            Opcodes.IRETURN -> "IRETURN"
            Opcodes.ARETURN -> "ARETURN"
            Opcodes.ICONST_0 -> "ICONST_0"
            Opcodes.ICONST_1 -> "ICONST_1"
            Opcodes.IFEQ -> "IFEQ"
            Opcodes.IFNE -> "IFNE"
            Opcodes.IFLT -> "IFLT"
            Opcodes.IFGE -> "IFGE"
            Opcodes.IFGT -> "IFGT"
            Opcodes.IFLE -> "IFLE"
            Opcodes.IF_ICMPEQ -> "IF_ICMPEQ"
            Opcodes.IF_ICMPNE -> "IF_ICMPNE"
            Opcodes.IF_ICMPLT -> "IF_ICMPLT"
            Opcodes.IF_ICMPGE -> "IF_ICMPGE"
            Opcodes.IF_ICMPGT -> "IF_ICMPGT"
            Opcodes.IF_ICMPLE -> "IF_ICMPLE"
            Opcodes.RETURN -> "RETURN"
            Opcodes.IINC -> "IINC"
            Opcodes.INEG -> "INEG"
            else -> "opcode_$opcode"
        }
}
