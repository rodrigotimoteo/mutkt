package com.github.rodrigotimoteo.mutation.mutator

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Main mutation engine that applies mutations to class bytecode.
 * Uses ASM to visit and transform bytecode instructions.
 */
class Mutator(
    private val enabledOperators: Set<MutationOperator> = MutationOperator.MVP_OPERATORS
) {

    /**
     * Scans a class for mutation points without applying mutations.
     * Returns list of potential mutations.
     */
    fun scanMutations(classBytes: ByteArray): List<MutationInfo> {
        val mutations = mutableListOf<MutationInfo>()
        val reader = ClassReader(classBytes)
        val visitor = MutationScannerVisitor(mutations, enabledOperators)
        reader.accept(visitor, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        return mutations
    }

    /**
     * Applies a specific mutation to class bytecode.
     * Returns mutated bytecode.
     */
    fun applyMutation(
        classBytes: ByteArray,
        targetMutation: MutationInfo
    ): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        val reader = ClassReader(classBytes)
        val visitor = MutationApplierVisitor(writer, targetMutation, enabledOperators)
        reader.accept(visitor, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        return writer.toByteArray()
    }

    /**
     * Generates all possible mutants for a class.
     * Returns list of (mutation, mutatedBytecode) pairs.
     */
    fun generateMutants(classBytes: ByteArray): List<Pair<MutationInfo, ByteArray>> {
        val mutations = scanMutations(classBytes)
        return mutations.map { mutation ->
            val mutatedBytes = applyMutation(classBytes, mutation)
            mutation to mutatedBytes
        }
    }
}

/**
 * ClassVisitor that scans for mutation points.
 */
private class MutationScannerVisitor(
    private val mutations: MutableList<MutationInfo>,
    private val enabledOperators: Set<MutationOperator>
) : ClassVisitor(Opcodes.ASM9) {

    private var currentClassName = ""
    private var isKotlinClass = false

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        currentClassName = name.replace('/', '.')
        // Check for Kotlin @Metadata annotation
        isKotlinClass = false // Will be set in visitAnnotation
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitAnnotation(desc: String, visible: Boolean): org.objectweb.asm.AnnotationVisitor? {
        if (desc == "Lkotlin/Metadata;") {
            isKotlinClass = true
        }
        return super.visitAnnotation(desc, visible)
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor? {
        val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
        if (mv == null) return null

        // Skip synthetic, bridge, and constructor methods
        if ((access and Opcodes.ACC_SYNTHETIC) != 0) return mv
        if ((access and Opcodes.ACC_BRIDGE) != 0) return mv
        if (name.startsWith("<")) return mv // Skip constructors and <clinit>

        // Skip Kotlin synthetic methods
        if (isKotlinClass && isKotlinSynthetic(name)) return mv

        return MutationScannerMethodVisitor(mv, currentClassName, name, descriptor, mutations, enabledOperators)
    }

    private fun isKotlinSynthetic(name: String): Boolean {
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
}

/**
 * MethodVisitor that scans for mutation points.
 */
private class MutationScannerMethodVisitor(
    mv: MethodVisitor,
    private val className: String,
    private val methodName: String,
    private val methodDescriptor: String,
    private val mutations: MutableList<MutationInfo>,
    private val enabledOperators: Set<MutationOperator>
) : MethodVisitor(Opcodes.ASM9, mv) {

    private var currentLineNumber = -1

    override fun visitLineNumber(line: Int, start: org.objectweb.asm.Label?) {
        currentLineNumber = line
        super.visitLineNumber(line, start)
    }

    override fun visitJumpInsn(opcode: Int, label: org.objectweb.asm.Label) {
        checkConditionalMutations(opcode)
        super.visitJumpInsn(opcode, label)
    }

    override fun visitInsn(opcode: Int) {
        checkArithmeticMutations(opcode)
        checkReturnMutations(opcode)
        super.visitInsn(opcode)
    }

    override fun visitIincInsn(varIndex: Int, increment: Int) {
        checkArithmeticIinc(increment)
        super.visitIincInsn(varIndex, increment)
    }

    private fun checkConditionalMutations(opcode: Int) {
        if (MutationOperator.CONDITIONALS_BOUNDARY in enabledOperators) {
            val mutated = ConditionalMutator.mutateBoundaryStatic(opcode)
            if (mutated != opcode) {
                mutations.add(MutationInfo(
                    operator = MutationOperator.CONDITIONALS_BOUNDARY,
                    className = className,
                    methodName = methodName,
                    methodDescriptor = methodDescriptor,
                    lineNumber = currentLineNumber,
                    description = "Boundary: $opcode -> $mutated",
                    originalOpcode = opcode,
                    mutatedOpcode = mutated
                ))
            }
        }
        if (MutationOperator.NEGATE_CONDITIONALS in enabledOperators) {
            val mutated = ConditionalMutator.mutateNegateStatic(opcode)
            if (mutated != opcode) {
                mutations.add(MutationInfo(
                    operator = MutationOperator.NEGATE_CONDITIONALS,
                    className = className,
                    methodName = methodName,
                    methodDescriptor = methodDescriptor,
                    lineNumber = currentLineNumber,
                    description = "Negate: $opcode -> $mutated",
                    originalOpcode = opcode,
                    mutatedOpcode = mutated
                ))
            }
        }
        if (MutationOperator.INVERT_NEGS in enabledOperators) {
            val mutated = InvertNegsMutator.mutateStatic(opcode)
            if (mutated != opcode) {
                mutations.add(MutationInfo(
                    operator = MutationOperator.INVERT_NEGS,
                    className = className,
                    methodName = methodName,
                    methodDescriptor = methodDescriptor,
                    lineNumber = currentLineNumber,
                    description = "Invert: $opcode -> $mutated",
                    originalOpcode = opcode,
                    mutatedOpcode = mutated
                ))
            }
        }
    }

    private fun checkArithmeticMutations(opcode: Int) {
        if (MutationOperator.ARITHMETIC in enabledOperators) {
            val mutated = ArithmeticMutator.mutateStatic(opcode)
            if (mutated != opcode) {
                mutations.add(MutationInfo(
                    operator = MutationOperator.ARITHMETIC,
                    className = className,
                    methodName = methodName,
                    methodDescriptor = methodDescriptor,
                    lineNumber = currentLineNumber,
                    description = "Arithmetic: $opcode -> $mutated",
                    originalOpcode = opcode,
                    mutatedOpcode = mutated
                ))
            }
        }
    }

    private fun checkArithmeticIinc(increment: Int) {
        if (MutationOperator.ARITHMETIC in enabledOperators) {
            val mutated = when (increment) {
                1 -> -1
                -1 -> 1
                else -> increment
            }
            if (mutated != increment) {
                mutations.add(MutationInfo(
                    operator = MutationOperator.ARITHMETIC,
                    className = className,
                    methodName = methodName,
                    methodDescriptor = methodDescriptor,
                    lineNumber = currentLineNumber,
                    description = "IINC: $increment -> $mutated",
                    originalOpcode = Opcodes.IINC,
                    mutatedOpcode = Opcodes.IINC
                ))
            }
        }
    }

    private fun checkReturnMutations(opcode: Int) {
        val returnType = Type.getReturnType(methodDescriptor)

        if (MutationOperator.RETURN_VALS in enabledOperators) {
            val mutated = ReturnValueMutator.mutateReturnStatic(opcode, returnType)
            if (mutated != opcode) {
                mutations.add(MutationInfo(
                    operator = MutationOperator.RETURN_VALS,
                    className = className,
                    methodName = methodName,
                    methodDescriptor = methodDescriptor,
                    lineNumber = currentLineNumber,
                    description = "Return: $opcode -> constant",
                    originalOpcode = opcode,
                    mutatedOpcode = mutated
                ))
            }
        }

        if (MutationOperator.NULL_RETURNS in enabledOperators && opcode == Opcodes.ARETURN) {
            mutations.add(MutationInfo(
                operator = MutationOperator.NULL_RETURNS,
                className = className,
                methodName = methodName,
                methodDescriptor = methodDescriptor,
                lineNumber = currentLineNumber,
                description = "Return null",
                originalOpcode = opcode,
                mutatedOpcode = opcode
            ))
        }

        if (MutationOperator.EMPTY_RETURNS in enabledOperators && opcode == Opcodes.ARETURN) {
            if (ReturnValueMutator.isCollectionOrArrayStatic(returnType)) {
                mutations.add(MutationInfo(
                    operator = MutationOperator.EMPTY_RETURNS,
                    className = className,
                    methodName = methodName,
                    methodDescriptor = methodDescriptor,
                    lineNumber = currentLineNumber,
                    description = "Return empty collection/array",
                    originalOpcode = opcode,
                    mutatedOpcode = opcode
                ))
            }
        }
    }
}

/**
 * ClassVisitor that applies a specific mutation.
 */
private class MutationApplierVisitor(
    writer: ClassWriter,
    private val targetMutation: MutationInfo,
    private val enabledOperators: Set<MutationOperator>
) : ClassVisitor(Opcodes.ASM9, writer) {

    private var currentClassName = ""
    private var isKotlinClass = false
    private var mutationApplied = false

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        currentClassName = name.replace('/', '.')
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitAnnotation(desc: String, visible: Boolean): org.objectweb.asm.AnnotationVisitor? {
        if (desc == "Lkotlin/Metadata;") {
            isKotlinClass = true
        }
        return super.visitAnnotation(desc, visible)
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor? {
        val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
        if (mv == null) return null

        if ((access and Opcodes.ACC_SYNTHETIC) != 0) return mv
        if ((access and Opcodes.ACC_BRIDGE) != 0) return mv
        if (name.startsWith("<")) return mv
        if (isKotlinClass && isKotlinSyntheticMethod(name)) return mv

        return MutationApplierMethodVisitor(
            mv,
            currentClassName,
            name,
            descriptor,
            targetMutation,
            enabledOperators
        )
    }
}

/**
 * MethodVisitor that applies a specific mutation.
 */
private class MutationApplierMethodVisitor(
    mv: MethodVisitor,
    private val className: String,
    private val methodName: String,
    private val methodDescriptor: String,
    private val targetMutation: MutationInfo,
    private val enabledOperators: Set<MutationOperator>
) : MethodVisitor(Opcodes.ASM9, mv) {

    private var currentLineNumber = -1
    private var applied = false

    override fun visitLineNumber(line: Int, start: org.objectweb.asm.Label?) {
        currentLineNumber = line
        super.visitLineNumber(line, start)
    }

    override fun visitJumpInsn(opcode: Int, label: org.objectweb.asm.Label) {
        if (!applied && currentLineNumber == targetMutation.lineNumber && opcode == targetMutation.originalOpcode) {
            val mutatedOpcode = getMutatedOpcode(opcode)
            if (mutatedOpcode != opcode) {
                super.visitJumpInsn(mutatedOpcode, label)
                applied = true
                return
            }
        }
        super.visitJumpInsn(opcode, label)
    }

    override fun visitInsn(opcode: Int) {
        if (!applied && currentLineNumber == targetMutation.lineNumber && opcode == targetMutation.originalOpcode) {
            val mutatedOpcode = getMutatedOpcode(opcode)
            if (mutatedOpcode != opcode) {
                // For return mutations, we need special handling
                if (targetMutation.operator == MutationOperator.RETURN_VALS ||
                    targetMutation.operator == MutationOperator.NULL_RETURNS ||
                    targetMutation.operator == MutationOperator.EMPTY_RETURNS) {
                    applyReturnMutation(opcode)
                    applied = true
                    return
                } else {
                    super.visitInsn(mutatedOpcode)
                    applied = true
                    return
                }
            }
        }
        super.visitInsn(opcode)
    }

    override fun visitIincInsn(varIndex: Int, increment: Int) {
        if (!applied && currentLineNumber == targetMutation.lineNumber &&
            targetMutation.operator == MutationOperator.ARITHMETIC) {
            val mutated = when (increment) {
                1 -> -1
                -1 -> 1
                else -> increment
            }
            if (mutated != increment) {
                super.visitIincInsn(varIndex, mutated)
                applied = true
                return
            }
        }
        super.visitIincInsn(varIndex, increment)
    }

    private fun getMutatedOpcode(opcode: Int): Int {
        return when (targetMutation.operator) {
            MutationOperator.CONDITIONALS_BOUNDARY -> ConditionalMutator.mutateBoundaryStatic(opcode)
            MutationOperator.NEGATE_CONDITIONALS -> ConditionalMutator.mutateNegateStatic(opcode)
            MutationOperator.INVERT_NEGS -> InvertNegsMutator.mutateStatic(opcode)
            MutationOperator.ARITHMETIC -> ArithmeticMutator.mutateStatic(opcode)
            else -> opcode
        }
    }

    private fun applyReturnMutation(opcode: Int) {
        val returnType = Type.getReturnType(methodDescriptor)
        when (targetMutation.operator) {
            MutationOperator.RETURN_VALS -> {
                when (opcode) {
                    Opcodes.IRETURN -> { mv.visitInsn(Opcodes.ICONST_0); mv.visitInsn(Opcodes.IRETURN) }
                    Opcodes.LRETURN -> { mv.visitInsn(Opcodes.LCONST_0); mv.visitInsn(Opcodes.LRETURN) }
                    Opcodes.FRETURN -> { mv.visitInsn(Opcodes.FCONST_0); mv.visitInsn(Opcodes.FRETURN) }
                    Opcodes.DRETURN -> { mv.visitInsn(Opcodes.DCONST_0); mv.visitInsn(Opcodes.DRETURN) }
                    Opcodes.ARETURN -> { mv.visitInsn(Opcodes.ACONST_NULL); mv.visitInsn(Opcodes.ARETURN) }
                    Opcodes.RETURN -> mv.visitInsn(Opcodes.RETURN)
                }
            }
            MutationOperator.NULL_RETURNS -> {
                if (opcode == Opcodes.ARETURN) {
                    mv.visitInsn(Opcodes.ACONST_NULL)
                    mv.visitInsn(Opcodes.ARETURN)
                } else {
                    super.visitInsn(opcode)
                }
            }
            MutationOperator.EMPTY_RETURNS -> {
                if (opcode == Opcodes.ARETURN && ReturnValueMutator.isCollectionOrArrayStatic(returnType)) {
                    applyEmptyReturn(returnType)
                } else {
                    super.visitInsn(opcode)
                }
            }
            else -> super.visitInsn(opcode)
        }
    }

    private fun applyEmptyReturn(returnType: Type) {
        val className = returnType.className.replace('/', '.')
        when {
            returnType.sort == Type.ARRAY -> {
                val elementType = returnType.elementType
                mv.visitInsn(Opcodes.ICONST_0)
                mv.visitTypeInsn(Opcodes.ANEWARRAY, elementType.internalName)
                mv.visitInsn(Opcodes.ARETURN)
            }
            className.contains("List") || className.contains("MutableList") -> {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Collections", "emptyList", "()Ljava/util/List;", false)
                mv.visitInsn(Opcodes.ARETURN)
            }
            className.contains("Set") || className.contains("MutableSet") -> {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Collections", "emptySet", "()Ljava/util/Set;", false)
                mv.visitInsn(Opcodes.ARETURN)
            }
            className.contains("Map") || className.contains("MutableMap") -> {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Collections", "emptyMap", "()Ljava/util/Map;", false)
                mv.visitInsn(Opcodes.ARETURN)
            }
            else -> mv.visitInsn(Opcodes.ARETURN)
        }
    }
}

// Static helper functions for mutation logic
private object ConditionalMutator {
    fun mutateBoundaryStatic(opcode: Int): Int = when (opcode) {
        Opcodes.IFGE -> Opcodes.IFGT
        Opcodes.IFGT -> Opcodes.IFGE
        Opcodes.IFLE -> Opcodes.IFLT
        Opcodes.IFLT -> Opcodes.IFLE
        Opcodes.IF_ICMPGE -> Opcodes.IF_ICMPGT
        Opcodes.IF_ICMPGT -> Opcodes.IF_ICMPGE
        Opcodes.IF_ICMPLE -> Opcodes.IF_ICMPLT
        Opcodes.IF_ICMPLT -> Opcodes.IF_ICMPLE
        Opcodes.IF_ACMPEQ -> Opcodes.IF_ACMPNE
        Opcodes.IF_ACMPNE -> Opcodes.IF_ACMPEQ
        else -> opcode
    }

    fun mutateNegateStatic(opcode: Int): Int = when (opcode) {
        Opcodes.IFEQ -> Opcodes.IFNE
        Opcodes.IFNE -> Opcodes.IFEQ
        Opcodes.IFLT -> Opcodes.IFGE
        Opcodes.IFGE -> Opcodes.IFLT
        Opcodes.IFGT -> Opcodes.IFLE
        Opcodes.IFLE -> Opcodes.IFGT
        Opcodes.IF_ICMPEQ -> Opcodes.IF_ICMPNE
        Opcodes.IF_ICMPNE -> Opcodes.IF_ICMPEQ
        Opcodes.IF_ICMPLT -> Opcodes.IF_ICMPGE
        Opcodes.IF_ICMPGE -> Opcodes.IF_ICMPLT
        Opcodes.IF_ICMPGT -> Opcodes.IF_ICMPLE
        Opcodes.IF_ICMPLE -> Opcodes.IF_ICMPGT
        Opcodes.IF_ACMPEQ -> Opcodes.IF_ACMPNE
        Opcodes.IF_ACMPNE -> Opcodes.IF_ACMPEQ
        Opcodes.IFNULL -> Opcodes.IFNONNULL
        Opcodes.IFNONNULL -> Opcodes.IFNULL
        else -> opcode
    }
}

private object ArithmeticMutator {
    fun mutateStatic(opcode: Int): Int = when (opcode) {
        Opcodes.IADD -> Opcodes.ISUB
        Opcodes.ISUB -> Opcodes.IADD
        Opcodes.IMUL -> Opcodes.IDIV
        Opcodes.IDIV -> Opcodes.IMUL
        Opcodes.IREM -> Opcodes.IMUL
        Opcodes.LADD -> Opcodes.LSUB
        Opcodes.LSUB -> Opcodes.LADD
        Opcodes.LMUL -> Opcodes.LDIV
        Opcodes.LDIV -> Opcodes.LMUL
        Opcodes.LREM -> Opcodes.LMUL
        Opcodes.FADD -> Opcodes.FSUB
        Opcodes.FSUB -> Opcodes.FADD
        Opcodes.FMUL -> Opcodes.FDIV
        Opcodes.FDIV -> Opcodes.FMUL
        Opcodes.FREM -> Opcodes.FMUL
        Opcodes.DADD -> Opcodes.DSUB
        Opcodes.DSUB -> Opcodes.DADD
        Opcodes.DMUL -> Opcodes.DDIV
        Opcodes.DDIV -> Opcodes.DMUL
        Opcodes.DREM -> Opcodes.DMUL
        Opcodes.INEG -> Opcodes.NOP
        Opcodes.LNEG -> Opcodes.NOP
        Opcodes.FNEG -> Opcodes.NOP
        Opcodes.DNEG -> Opcodes.NOP
        else -> opcode
    }
}

private object InvertNegsMutator {
    fun mutateStatic(opcode: Int): Int = when (opcode) {
        Opcodes.IFEQ -> Opcodes.IFNE
        Opcodes.IFNE -> Opcodes.IFEQ
        else -> opcode
    }
}

private object ReturnValueMutator {
    fun mutateReturnStatic(opcode: Int, returnType: Type): Int = when (opcode) {
        Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN -> opcode
        else -> opcode
    }

    fun isCollectionOrArrayStatic(type: Type): Boolean {
        return type.sort == Type.ARRAY ||
               type.className.startsWith("java/util/List") ||
               type.className.startsWith("java/util/Set") ||
               type.className.startsWith("java/util/Collection") ||
               type.className.startsWith("kotlin/collections/")
    }
}

/**
 * Check if a Kotlin method is synthetic (generated by compiler).
 */
private fun isKotlinSyntheticMethod(name: String): Boolean {
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