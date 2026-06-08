package com.github.rodrigotimoteo.mutation.mutator

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Main mutation engine that applies mutations to class bytecode.
 *
 * Uses ASM to visit and transform bytecode instructions. Supports multiple
 * mutation operators including arithmetic, conditional, return value, and
 * Kotlin-specific mutations.
 *
 * Example:
 * ```kotlin
 * val mutator = Mutator(MutationOperator.MVP_OPERATORS)
 * val mutations = mutator.scanMutations(classBytes)
 * val mutated = mutator.applyMutation(classBytes, mutations.first())
 * ```
 *
 * @property enabledOperators Set of mutation operators to apply
 * @see MutationOperator for available operators
 * @see MutationInfo for mutation point details
 */
class Mutator(
    private val enabledOperators: Set<MutationOperator> = MutationOperator.MVP_OPERATORS,
) {
    /**
     * Scans a class for mutation points without applying mutations.
     * Returns list of potential mutations.
     */
    fun scanMutations(classBytes: ByteArray): List<MutationInfo> {
        val mutations = mutableListOf<MutationInfo>()
        val reader = ClassReader(classBytes)
        val visitor = MutationScannerVisitor(mutations, enabledOperators)
        reader.accept(visitor, ClassReader.SKIP_FRAMES)
        return mutations
    }

    /**
     * Applies a specific mutation to class bytecode.
     * Returns mutated bytecode.
     */
    fun applyMutation(
        classBytes: ByteArray,
        targetMutation: MutationInfo,
    ): ByteArray {
        val writer =
            object : ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES) {
                override fun getCommonSuperClass(
                    type1: String,
                    type2: String,
                ): String {
                    val loader = Thread.currentThread().contextClassLoader ?: javaClass.classLoader
                    if (type1 == type2) return type1
                    if (type1 == "java/lang/Object" || type2 == "java/lang/Object") return "java/lang/Object"
                    val c1 =
                        try {
                            Class.forName(type1.replace('/', '.'), false, loader)
                        } catch (e: ClassNotFoundException) {
                            null
                        }
                    val c2 =
                        try {
                            Class.forName(type2.replace('/', '.'), false, loader)
                        } catch (e: ClassNotFoundException) {
                            null
                        }
                    if (c1 == null && c2 == null) return "java/lang/Object"
                    if (c1 == null) return type2
                    if (c2 == null) return type1
                    var t: Class<*>? = c1
                    while (t != null) {
                        if (t.isAssignableFrom(c2)) return t.name.replace('.', '/')
                        t = t.superclass
                    }
                    return "java/lang/Object"
                }
            }
        val reader = ClassReader(classBytes)
        val visitor = MutationApplierVisitor(writer, targetMutation, enabledOperators)
        reader.accept(visitor, ClassReader.SKIP_FRAMES)
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
    private val enabledOperators: Set<MutationOperator>,
) : ClassVisitor(Opcodes.ASM9) {
    private var currentClassName = ""
    private var isKotlinClass = false
    private var classSuppressed = false
    private var suppressedOperators = emptySet<String>()

    /**
     * Check if mutation should be suppressed.
     */
    private fun isSuppressed(operator: MutationOperator): Boolean {
        if (classSuppressed) return true
        if (suppressedOperators.contains(operator.operatorName)) return true
        return false
    }

    /**
     * Add mutation if not suppressed.
     */
    private fun addMutation(mutation: MutationInfo) {
        if (!isSuppressed(mutation.operator)) {
            mutations.add(mutation)
        }
    }

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?,
    ) {
        currentClassName = name.replace('/', '.')
        // Check for Kotlin @Metadata annotation
        isKotlinClass = false // Will be set in visitAnnotation
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitAnnotation(
        desc: String,
        visible: Boolean,
    ): org.objectweb.asm.AnnotationVisitor? {
        if (desc == "Lkotlin/Metadata;") {
            isKotlinClass = true
        }
        // Check for @SuppressMutations annotation
        if (desc == "Lcom/github/rodrigotimoteo/mutation/annotation/SuppressMutations;") {
            // Parse the annotation to extract specific operators to suppress.
            // If operators list is empty, suppress ALL mutations.
            // If operators list is non-empty, suppress only listed operators.
            var hasExplicitOperators = false
            return object : org.objectweb.asm.AnnotationVisitor(Opcodes.ASM9) {
                override fun visitArray(name: String?): org.objectweb.asm.AnnotationVisitor? {
                    if (name == "operators") {
                        return object : org.objectweb.asm.AnnotationVisitor(Opcodes.ASM9) {
                            override fun visit(
                                name: String?,
                                value: Any?,
                            ) {
                                if (value is String) {
                                    suppressedOperators = suppressedOperators + value
                                    hasExplicitOperators = true
                                }
                            }
                        }
                    }
                    return super.visitArray(name)
                }

                override fun visitEnd() {
                    super.visitEnd()
                    // Only suppress ALL mutations when no specific operators were listed
                    if (!hasExplicitOperators) {
                        classSuppressed = true
                    }
                }
            }
        }
        return super.visitAnnotation(desc, visible)
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor? {
        // Always visit methods - don't rely on super.visitMethod() which returns null
        // when no delegate ClassVisitor is set.

        // Skip synthetic, bridge, and constructor methods
        if ((access and Opcodes.ACC_SYNTHETIC) != 0) return null
        if ((access and Opcodes.ACC_BRIDGE) != 0) return null
        if (name.startsWith("<")) return null

        // Skip Kotlin synthetic methods
        if (isKotlinClass && isKotlinSynthetic(name)) return null

        return MutationScannerMethodVisitor(
            className = currentClassName,
            methodName = name,
            methodDescriptor = descriptor,
            mutations = mutations,
            enabledOperators = enabledOperators,
            classSuppressed = classSuppressed,
            suppressedOperators = suppressedOperators,
            isKotlinClass = isKotlinClass,
        )
    }

    private fun isKotlinSynthetic(name: String): Boolean {
        // Only match known compiler-generated patterns, not general substring matches
        return name == "copy\$default" ||
            name.startsWith("component") && name.endsWith("\$default") ||
            name.endsWith("\$serializer") ||
            name == "<init>\$default" ||
            name == "toString\$default" ||
            name == "hashCode\$default" ||
            name == "equals\$default"
    }
}

/**
 * MethodVisitor that scans for mutation points.
 */
private class MutationScannerMethodVisitor(
    private val className: String,
    private val methodName: String,
    private val methodDescriptor: String,
    private val mutations: MutableList<MutationInfo>,
    private val enabledOperators: Set<MutationOperator>,
    private val classSuppressed: Boolean = false,
    private val suppressedOperators: Set<String> = emptySet(),
    private val isKotlinClass: Boolean = false,
) : MethodVisitor(Opcodes.ASM9) {
    private var currentLineNumber = -1
    private var instanceofCount = 0

    /**
     * Check if mutation should be suppressed.
     */
    private fun isSuppressed(operator: MutationOperator): Boolean {
        if (classSuppressed) return true
        if (suppressedOperators.contains(operator.operatorName)) return true
        return false
    }

    private fun tryAddMutation(mutation: MutationInfo) {
        if (!isSuppressed(mutation.operator)) {
            mutations.add(mutation)
        }
    }

    override fun visitLineNumber(
        line: Int,
        start: org.objectweb.asm.Label?,
    ) {
        currentLineNumber = line
        super.visitLineNumber(line, start)
    }

    override fun visitJumpInsn(
        opcode: Int,
        label: org.objectweb.asm.Label,
    ) {
        checkConditionalMutations(opcode)
        checkNullSafetyBranchMutations(opcode, label)
        super.visitJumpInsn(opcode, label)
    }

    override fun visitTypeInsn(
        opcode: Int,
        type: String,
    ) {
        checkSealedWhenInstanceofMutations(opcode, type)
        super.visitTypeInsn(opcode, type)
    }

    override fun visitInsn(opcode: Int) {
        checkArithmeticMutations(opcode)
        checkReturnMutations(opcode)
        checkBooleanReturnMutations(opcode)
        super.visitInsn(opcode)
    }

    override fun visitIincInsn(
        varIndex: Int,
        increment: Int,
    ) {
        checkArithmeticIinc(increment)
        checkIncrementMutations(increment)
        super.visitIincInsn(varIndex, increment)
    }

    override fun visitMethodInsn(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        isInterface: Boolean,
    ) {
        checkVoidMethodCallMutations(opcode, owner, name, descriptor)
        checkConstructorCallMutations(opcode, owner, name)
        checkNonVoidMethodCallMutations(opcode, owner, name, descriptor)
        checkKotlinMutatorMutations(opcode, owner, name, descriptor)
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
    }

    override fun visitTableSwitchInsn(
        min: Int,
        max: Int,
        dflt: org.objectweb.asm.Label?,
        vararg labels: org.objectweb.asm.Label?,
    ) {
        checkSealedWhenMutations(Opcodes.TABLESWITCH, labels.size)
        super.visitTableSwitchInsn(min, max, dflt, *labels)
    }

    override fun visitLookupSwitchInsn(
        dflt: org.objectweb.asm.Label?,
        keys: IntArray?,
        labels: Array<out org.objectweb.asm.Label?>,
    ) {
        checkSealedWhenMutations(Opcodes.LOOKUPSWITCH, labels.size)
        super.visitLookupSwitchInsn(dflt, keys, labels)
    }

    /**
     * Detect Kotlin-specific patterns and create mutations.
     * - DATA_CLASS_COPY: INVOKESPECIAL to copy() on data class
     * - COROUTINE: suspend function calls and builder calls
     * - NULL_SAFETY: kotlin.throwNpe, kotlin.checkNotNull, Intrinsics
     * - SEALED_WHEN: handled at tableswitch/lookupswitch
     */
    private fun checkKotlinMutatorMutations(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
    ) {
        // DATA_CLASS_COPY: call to copy() or copy$default() method (data class generated)
        // Kotlin data classes use INVOKEVIRTUAL for copy() and INVOKESTATIC for copy$default()
        if (MutationOperator.DATA_CLASS_COPY in enabledOperators) {
            if ((opcode == Opcodes.INVOKESPECIAL || opcode == Opcodes.INVOKEVIRTUAL) && name == "copy") {
                tryAddMutation(
                    MutationInfo(
                        operator = MutationOperator.DATA_CLASS_COPY,
                        className = className,
                        methodName = methodName,
                        methodDescriptor = methodDescriptor,
                        lineNumber = currentLineNumber,
                        description = "Data class copy() call mutation",
                        originalOpcode = opcode,
                        mutatedOpcode = Opcodes.NOP,
                    ),
                )
            }
            // Also detect copy$default() — Kotlin compiles copy(age = x) to this static method
            if (opcode == Opcodes.INVOKESTATIC && name == "copy\$default") {
                tryAddMutation(
                    MutationInfo(
                        operator = MutationOperator.DATA_CLASS_COPY,
                        className = className,
                        methodName = methodName,
                        methodDescriptor = methodDescriptor,
                        lineNumber = currentLineNumber,
                        description = "Data class copy\$default() call mutation",
                        originalOpcode = opcode,
                        mutatedOpcode = Opcodes.NOP,
                    ),
                )
            }
        }

        // COROUTINE: detect suspend markers, coroutine builders
        if (MutationOperator.COROUTINE in enabledOperators) {
            // Coroutine builder calls: kotlinx coroutines builders
            if (opcode == Opcodes.INVOKESTATIC &&
                (
                    owner == "kotlinx/coroutines/BuildersKt" ||
                        owner == "kotlinx/coroutines/JobKt" ||
                        owner == "kotlinx/coroutines/GlobalScope"
                )
            ) {
                tryAddMutation(
                    MutationInfo(
                        operator = MutationOperator.COROUTINE,
                        className = className,
                        methodName = methodName,
                        methodDescriptor = methodDescriptor,
                        lineNumber = currentLineNumber,
                        description = "Coroutine builder call: $owner.$name",
                        originalOpcode = opcode,
                        mutatedOpcode = Opcodes.NOP,
                    ),
                )
            }
        }

        // NULL_SAFETY: detect Kotlin null check intrinsics
        if (MutationOperator.NULL_SAFETY in enabledOperators) {
            if (opcode == Opcodes.INVOKESTATIC &&
                owner == "kotlin/jvm/internal/Intrinsics" &&
                (
                    name == "checkNotNull" ||
                        name == "checkNotNullParameter" ||
                        name == "checkNotNullExpressionValue" ||
                        name == "throwUninitializedPropertyAccessException"
                )
            ) {
                tryAddMutation(
                    MutationInfo(
                        operator = MutationOperator.NULL_SAFETY,
                        className = className,
                        methodName = methodName,
                        methodDescriptor = methodDescriptor,
                        lineNumber = currentLineNumber,
                        description = "Kotlin null check: $name mutation",
                        originalOpcode = opcode,
                        mutatedOpcode = Opcodes.NOP,
                    ),
                )
            }
        }
    }

    /**
     * Detect sealed class when expressions and create mutations.
     * When expressions compile to tableswitch/lookupswitch instructions.
     */
    private fun checkSealedWhenMutations(
        opcode: Int,
        branchCount: Int,
    ) {
        if (MutationOperator.SEALED_WHEN in enabledOperators && isKotlinClass) {
            // Create a mutation for each branch: remove it by redirecting to default
            for (i in 0 until branchCount) {
                tryAddMutation(
                    MutationInfo(
                        operator = MutationOperator.SEALED_WHEN,
                        className = className,
                        methodName = methodName,
                        methodDescriptor = methodDescriptor,
                        lineNumber = currentLineNumber,
                        description = "Remove when branch $i of $branchCount",
                        originalOpcode = opcode,
                        mutatedOpcode = opcode,
                        metadata =
                            mapOf(
                                "branchIndex" to i.toString(),
                                "branchCount" to branchCount.toString(),
                            ),
                    ),
                )
            }
        }
    }

    /**
     * Detect sealed class when expressions via instanceof chain pattern.
     * Kotlin compiles sealed class when with data class subclasses to instanceof chains.
     */
    private fun checkSealedWhenInstanceofMutations(
        opcode: Int,
        type: String,
    ) {
        if (opcode == Opcodes.INSTANCEOF && MutationOperator.SEALED_WHEN in enabledOperators && isKotlinClass) {
            // Track instanceof instructions — if we see multiple instanceof on the same line,
            // it's likely a when-expression branch chain
            instanceofCount++
            if (instanceofCount >= 2) {
                // Create mutation: remove this instanceof branch by replacing with pop+iconst_0
                // This effectively makes the branch always false (skips it)
                tryAddMutation(
                    MutationInfo(
                        operator = MutationOperator.SEALED_WHEN,
                        className = className,
                        methodName = methodName,
                        methodDescriptor = methodDescriptor,
                        lineNumber = currentLineNumber,
                        description = "Remove when branch: instanceof ${type.substringAfterLast('/')}",
                        originalOpcode = Opcodes.INSTANCEOF,
                        mutatedOpcode = Opcodes.NOP,
                        metadata = mapOf("type" to type),
                    ),
                )
            }
        }
    }

    /**
     * Detect null safety mutations for IFNULL/IFNONNULL patterns.
     * Kotlin compiles ?. and ?: to IFNULL/IFNONNULL branch instructions.
     */
    private fun checkNullSafetyBranchMutations(
        opcode: Int,
        label: org.objectweb.asm.Label,
    ) {
        if (MutationOperator.NULL_SAFETY in enabledOperators && isKotlinClass) {
            when (opcode) {
                Opcodes.IFNULL -> {
                    // ?.safe call: ifnull skips the non-null path
                    // Mutation: replace ifnull with goto (always take non-null path)
                    tryAddMutation(
                        MutationInfo(
                            operator = MutationOperator.NULL_SAFETY,
                            className = className,
                            methodName = methodName,
                            methodDescriptor = methodDescriptor,
                            lineNumber = currentLineNumber,
                            description = "Remove null check: ?.",
                            originalOpcode = Opcodes.IFNULL,
                            mutatedOpcode = Opcodes.GOTO,
                        ),
                    )
                }
                Opcodes.IFNONNULL -> {
                    // ?: elvis: ifnonnull skips the default value path
                    // Mutation: replace ifnonnull with goto (always take default path)
                    tryAddMutation(
                        MutationInfo(
                            operator = MutationOperator.NULL_SAFETY,
                            className = className,
                            methodName = methodName,
                            methodDescriptor = methodDescriptor,
                            lineNumber = currentLineNumber,
                            description = "Remove null check: ?:",
                            originalOpcode = Opcodes.IFNONNULL,
                            mutatedOpcode = Opcodes.GOTO,
                        ),
                    )
                }
            }
        }
    }

    private fun checkConditionalMutations(opcode: Int) {
        if (MutationOperator.CONDITIONALS_BOUNDARY in enabledOperators) {
            val mutated = ConditionalMutator.mutateBoundaryStatic(opcode)
            if (mutated != opcode) {
                tryAddMutation(
                    MutationInfo(
                        operator = MutationOperator.CONDITIONALS_BOUNDARY,
                        className = className,
                        methodName = methodName,
                        methodDescriptor = methodDescriptor,
                        lineNumber = currentLineNumber,
                        description = "Boundary: $opcode -> $mutated",
                        originalOpcode = opcode,
                        mutatedOpcode = mutated,
                    ),
                )
            }
        }
        if (MutationOperator.NEGATE_CONDITIONALS in enabledOperators) {
            val mutated = ConditionalMutator.mutateNegateStatic(opcode)
            if (mutated != opcode) {
                tryAddMutation(
                    MutationInfo(
                        operator = MutationOperator.NEGATE_CONDITIONALS,
                        className = className,
                        methodName = methodName,
                        methodDescriptor = methodDescriptor,
                        lineNumber = currentLineNumber,
                        description = "Negate: $opcode -> $mutated",
                        originalOpcode = opcode,
                        mutatedOpcode = mutated,
                    ),
                )
            }
        }
    }

    private fun checkArithmeticMutations(opcode: Int) {
        if (MutationOperator.ARITHMETIC in enabledOperators) {
            val mutated = ArithmeticMutator.mutateStatic(opcode)
            if (mutated != opcode) {
                tryAddMutation(
                    MutationInfo(
                        operator = MutationOperator.ARITHMETIC,
                        className = className,
                        methodName = methodName,
                        methodDescriptor = methodDescriptor,
                        lineNumber = currentLineNumber,
                        description = "Arithmetic: $opcode -> $mutated",
                        originalOpcode = opcode,
                        mutatedOpcode = mutated,
                    ),
                )
            }
        }
    }

    private fun checkArithmeticIinc(increment: Int) {
        if (MutationOperator.ARITHMETIC in enabledOperators) {
            val mutated = -increment
            if (mutated != increment) {
                tryAddMutation(
                    MutationInfo(
                        operator = MutationOperator.ARITHMETIC,
                        className = className,
                        methodName = methodName,
                        methodDescriptor = methodDescriptor,
                        lineNumber = currentLineNumber,
                        description = "IINC: $increment -> $mutated",
                        originalOpcode = Opcodes.IINC,
                        mutatedOpcode = Opcodes.IINC,
                    ),
                )
            }
        }
    }

    private fun checkReturnMutations(opcode: Int) {
        val returnType = Type.getReturnType(methodDescriptor)

        if (MutationOperator.RETURN_VALS in enabledOperators) {
            if (opcode == Opcodes.IRETURN || opcode == Opcodes.LRETURN ||
                opcode == Opcodes.FRETURN || opcode == Opcodes.DRETURN ||
                opcode == Opcodes.ARETURN
            ) {
                tryAddMutation(
                    MutationInfo(
                        operator = MutationOperator.RETURN_VALS,
                        className = className,
                        methodName = methodName,
                        methodDescriptor = methodDescriptor,
                        lineNumber = currentLineNumber,
                        description = "Return: $opcode -> constant",
                        originalOpcode = opcode,
                        mutatedOpcode = opcode,
                    ),
                )
            }
        }

        if (MutationOperator.NULL_RETURNS in enabledOperators && opcode == Opcodes.ARETURN) {
            tryAddMutation(
                MutationInfo(
                    operator = MutationOperator.NULL_RETURNS,
                    className = className,
                    methodName = methodName,
                    methodDescriptor = methodDescriptor,
                    lineNumber = currentLineNumber,
                    description = "Return null",
                    originalOpcode = opcode,
                    mutatedOpcode = opcode,
                ),
            )
        }

        if (MutationOperator.EMPTY_RETURNS in enabledOperators && opcode == Opcodes.ARETURN) {
            if (ReturnValueMutator.isCollectionOrArrayStatic(returnType)) {
                tryAddMutation(
                    MutationInfo(
                        operator = MutationOperator.EMPTY_RETURNS,
                        className = className,
                        methodName = methodName,
                        methodDescriptor = methodDescriptor,
                        lineNumber = currentLineNumber,
                        description = "Return empty collection/array",
                        originalOpcode = opcode,
                        mutatedOpcode = opcode,
                    ),
                )
            }
        }
    }

    private fun checkBooleanReturnMutations(opcode: Int) {
        val returnType = Type.getReturnType(methodDescriptor)
        if (returnType.sort != Type.BOOLEAN) return

        if (MutationOperator.TRUE_RETURNS in enabledOperators && opcode == Opcodes.ICONST_1) {
            tryAddMutation(
                MutationInfo(
                    operator = MutationOperator.TRUE_RETURNS,
                    className = className,
                    methodName = methodName,
                    methodDescriptor = methodDescriptor,
                    lineNumber = currentLineNumber,
                    description = "Boolean return: true -> false",
                    originalOpcode = opcode,
                    mutatedOpcode = Opcodes.ICONST_0,
                ),
            )
        }
        if (MutationOperator.FALSE_RETURNS in enabledOperators && opcode == Opcodes.ICONST_0) {
            tryAddMutation(
                MutationInfo(
                    operator = MutationOperator.FALSE_RETURNS,
                    className = className,
                    methodName = methodName,
                    methodDescriptor = methodDescriptor,
                    lineNumber = currentLineNumber,
                    description = "Boolean return: false -> true",
                    originalOpcode = opcode,
                    mutatedOpcode = Opcodes.ICONST_1,
                ),
            )
        }
    }

    private fun checkIncrementMutations(increment: Int) {
        if (MutationOperator.INCREMENTS in enabledOperators) {
            val mutated = -increment
            if (mutated != increment) {
                tryAddMutation(
                    MutationInfo(
                        operator = MutationOperator.INCREMENTS,
                        className = className,
                        methodName = methodName,
                        methodDescriptor = methodDescriptor,
                        lineNumber = currentLineNumber,
                        description = "Increment: $increment -> $mutated",
                        originalOpcode = Opcodes.IINC,
                        mutatedOpcode = Opcodes.IINC,
                    ),
                )
            }
        }
    }

    private fun checkVoidMethodCallMutations(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
    ) {
        if (MutationOperator.VOID_METHOD_CALLS in enabledOperators) {
            val returnType = Type.getReturnType(descriptor)
            if (returnType.sort == Type.VOID && name != "<init>" && name != "<clinit>") {
                tryAddMutation(
                    MutationInfo(
                        operator = MutationOperator.VOID_METHOD_CALLS,
                        className = className,
                        methodName = methodName,
                        methodDescriptor = methodDescriptor,
                        lineNumber = currentLineNumber,
                        description = "Remove void call: $owner.$name",
                        originalOpcode = opcode,
                        mutatedOpcode = opcode,
                    ),
                )
            }
        }
    }

    private fun checkConstructorCallMutations(
        opcode: Int,
        owner: String,
        name: String,
    ) {
        if (MutationOperator.CONSTRUCTOR_CALLS in enabledOperators && name == "<init>") {
            tryAddMutation(
                MutationInfo(
                    operator = MutationOperator.CONSTRUCTOR_CALLS,
                    className = className,
                    methodName = methodName,
                    methodDescriptor = methodDescriptor,
                    lineNumber = currentLineNumber,
                    description = "Remove constructor call: $owner.<init>",
                    originalOpcode = opcode,
                    mutatedOpcode = opcode,
                ),
            )
        }
    }

    private fun checkNonVoidMethodCallMutations(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
    ) {
        if (MutationOperator.NON_VOID_METHOD_CALLS in enabledOperators) {
            val returnType = Type.getReturnType(descriptor)
            if (returnType.sort != Type.VOID && name != "<init>" && name != "<clinit>") {
                tryAddMutation(
                    MutationInfo(
                        operator = MutationOperator.NON_VOID_METHOD_CALLS,
                        className = className,
                        methodName = methodName,
                        methodDescriptor = methodDescriptor,
                        lineNumber = currentLineNumber,
                        description = "Remove non-void call: $owner.$name",
                        originalOpcode = opcode,
                        mutatedOpcode = opcode,
                    ),
                )
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
    private val enabledOperators: Set<MutationOperator>,
) : ClassVisitor(Opcodes.ASM9, writer) {
    private var currentClassName = ""
    private var isKotlinClass = false

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?,
    ) {
        currentClassName = name.replace('/', '.')
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitAnnotation(
        desc: String,
        visible: Boolean,
    ): org.objectweb.asm.AnnotationVisitor? {
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
        exceptions: Array<out String>?,
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
            enabledOperators,
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
    private val enabledOperators: Set<MutationOperator>,
) : MethodVisitor(Opcodes.ASM9, mv) {
    private var currentLineNumber = -1
    private var applied = false

    override fun visitLineNumber(
        line: Int,
        start: org.objectweb.asm.Label?,
    ) {
        currentLineNumber = line
        super.visitLineNumber(line, start)
    }

    override fun visitInsn(opcode: Int) {
        if (!applied && currentLineNumber == targetMutation.lineNumber && opcode == targetMutation.originalOpcode) {
            // Return mutations need special handling — they always match
            if (targetMutation.operator == MutationOperator.RETURN_VALS ||
                targetMutation.operator == MutationOperator.NULL_RETURNS ||
                targetMutation.operator == MutationOperator.EMPTY_RETURNS
            ) {
                applyReturnMutation(opcode)
                applied = true
                return
            }
            val mutatedOpcode = getMutatedOpcode(opcode)
            if (mutatedOpcode != opcode) {
                super.visitInsn(mutatedOpcode)
                applied = true
                return
            }
        }
        super.visitInsn(opcode)
    }

    override fun visitJumpInsn(
        opcode: Int,
        label: org.objectweb.asm.Label,
    ) {
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

    override fun visitTypeInsn(
        opcode: Int,
        type: String,
    ) {
        if (!applied && currentLineNumber == targetMutation.lineNumber &&
            opcode == targetMutation.originalOpcode &&
            targetMutation.operator == MutationOperator.SEALED_WHEN
        ) {
            // SEALED_WHEN instanceof mutation: replace instanceof with pop+iconst_0
            // This makes the branch always false (skips it)
            super.visitInsn(Opcodes.POP)
            super.visitInsn(Opcodes.ICONST_0)
            applied = true
            return
        }
        super.visitTypeInsn(opcode, type)
    }

    override fun visitIincInsn(
        varIndex: Int,
        increment: Int,
    ) {
        if (!applied && currentLineNumber == targetMutation.lineNumber) {
            when (targetMutation.operator) {
                MutationOperator.ARITHMETIC -> {
                    val mutated = -increment
                    if (mutated != increment) {
                        super.visitIincInsn(varIndex, mutated)
                        applied = true
                        return
                    }
                }
                MutationOperator.INCREMENTS -> {
                    super.visitIincInsn(varIndex, -increment)
                    applied = true
                    return
                }
                else -> {}
            }
        }
        super.visitIincInsn(varIndex, increment)
    }

    override fun visitTableSwitchInsn(
        min: Int,
        max: Int,
        dflt: org.objectweb.asm.Label?,
        vararg labels: org.objectweb.asm.Label?,
    ) {
        if (!applied && targetMutation.operator == MutationOperator.SEALED_WHEN &&
            currentLineNumber == targetMutation.lineNumber
        ) {
            // Redirect the target branch to the default label (effectively removing it)
            val branchIndex = targetMutation.metadata["branchIndex"]?.toIntOrNull() ?: -1
            if (branchIndex in labels.indices) {
                val newLabels =
                    labels.mapIndexed { i, label ->
                        if (i == branchIndex) dflt else label
                    }.toTypedArray()
                super.visitTableSwitchInsn(min, max, dflt, *newLabels)
                applied = true
                return
            }
        }
        super.visitTableSwitchInsn(min, max, dflt, *labels)
    }

    override fun visitLookupSwitchInsn(
        dflt: org.objectweb.asm.Label?,
        keys: IntArray?,
        labels: Array<out org.objectweb.asm.Label?>,
    ) {
        if (!applied && targetMutation.operator == MutationOperator.SEALED_WHEN &&
            currentLineNumber == targetMutation.lineNumber
        ) {
            val branchIndex = targetMutation.metadata["branchIndex"]?.toIntOrNull() ?: -1
            if (branchIndex in labels.indices) {
                val newLabels =
                    labels.mapIndexed { i, label ->
                        if (i == branchIndex) dflt else label
                    }.toTypedArray()
                super.visitLookupSwitchInsn(dflt, keys, newLabels)
                applied = true
                return
            }
        }
        super.visitLookupSwitchInsn(dflt, keys, labels)
    }

    override fun visitMethodInsn(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        isInterface: Boolean,
    ) {
        if (!applied && currentLineNumber == targetMutation.lineNumber) {
            when (targetMutation.operator) {
                MutationOperator.VOID_METHOD_CALLS -> {
                    if (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKESTATIC ||
                        opcode == Opcodes.INVOKEINTERFACE || opcode == Opcodes.INVOKESPECIAL
                    ) {
                        val returnType = Type.getReturnType(descriptor)
                        if (returnType.sort == Type.VOID) {
                            val argTypes = Type.getArgumentTypes(descriptor)
                            popArgs(argTypes)
                            if (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE ||
                                opcode == Opcodes.INVOKESPECIAL
                            ) {
                                mv.visitInsn(Opcodes.POP) // Pop receiver
                            }
                            applied = true
                            return
                        }
                    }
                }
                MutationOperator.CONSTRUCTOR_CALLS -> {
                    if (opcode == Opcodes.INVOKESPECIAL && name == "<init>") {
                        // Stack: [uninitialized_ref, uninitialized_ref, arg1, ..., argN]
                        // NEW+DUP created 2 refs. popArgs removes args. POP2 removes both refs.
                        val argTypes = Type.getArgumentTypes(descriptor)
                        popArgs(argTypes)
                        mv.visitInsn(Opcodes.POP2) // Pop both NEW+DUP refs
                        mv.visitInsn(Opcodes.ACONST_NULL) // Push null as replacement
                        applied = true
                        return
                    }
                }
                MutationOperator.NON_VOID_METHOD_CALLS -> {
                    val returnType = Type.getReturnType(descriptor)
                    if (returnType.sort != Type.VOID) {
                        val argTypes = Type.getArgumentTypes(descriptor)
                        popArgs(argTypes)
                        if (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE ||
                            opcode == Opcodes.INVOKESPECIAL
                        ) {
                            mv.visitInsn(Opcodes.POP) // Pop receiver for virtual/interface/special calls
                        }
                        pushDefaultValue(returnType)
                        applied = true
                        return
                    }
                }
                // Kotlin-specific operator appliers
                MutationOperator.DATA_CLASS_COPY -> {
                    if ((opcode == Opcodes.INVOKESPECIAL || opcode == Opcodes.INVOKEVIRTUAL) && name == "copy") {
                        // Remove copy() call: pop args + receiver, push null
                        val argTypes = Type.getArgumentTypes(descriptor)
                        popArgs(argTypes)
                        mv.visitInsn(Opcodes.POP) // Pop receiver
                        mv.visitInsn(Opcodes.ACONST_NULL)
                        applied = true
                        return
                    }
                }
                MutationOperator.COROUTINE -> {
                    // Remove coroutine builder call (static — no receiver on stack)
                    val argTypes = Type.getArgumentTypes(descriptor)
                    popArgs(argTypes)
                    pushDefaultValue(Type.getReturnType(descriptor))
                    applied = true
                    return
                }
                MutationOperator.NULL_SAFETY -> {
                    // Remove null check call (static — no receiver on stack)
                    // checkNotNull(Object) returns the checked value (non-void)
                    // checkNotNullParameter(Object, String) returns void
                    // throwUninitializedPropertyAccessException(String) returns void
                    val argTypes = Type.getArgumentTypes(descriptor)
                    popArgs(argTypes)
                    val returnType = Type.getReturnType(descriptor)
                    if (returnType.sort != Type.VOID) {
                        pushDefaultValue(returnType)
                    }
                    applied = true
                    return
                }
                else -> {}
            }
        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
    }

    private fun popArgs(argTypes: Array<Type>) {
        for (type in argTypes.reversed()) {
            if (type.sort == Type.LONG || type.sort == Type.DOUBLE) {
                mv.visitInsn(Opcodes.POP2)
            } else {
                mv.visitInsn(Opcodes.POP)
            }
        }
    }

    private fun pushDefaultValue(type: Type) {
        when (type.sort) {
            Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> mv.visitInsn(Opcodes.ICONST_0)
            Type.LONG -> mv.visitInsn(Opcodes.LCONST_0)
            Type.FLOAT -> mv.visitInsn(Opcodes.FCONST_0)
            Type.DOUBLE -> mv.visitInsn(Opcodes.DCONST_0)
            Type.ARRAY, Type.OBJECT -> mv.visitInsn(Opcodes.ACONST_NULL)
        }
    }

    private fun getMutatedOpcode(opcode: Int): Int {
        return when (targetMutation.operator) {
            MutationOperator.CONDITIONALS_BOUNDARY -> ConditionalMutator.mutateBoundaryStatic(opcode)
            MutationOperator.NEGATE_CONDITIONALS -> ConditionalMutator.mutateNegateStatic(opcode)
            MutationOperator.ARITHMETIC -> ArithmeticMutator.mutateStatic(opcode)
            MutationOperator.TRUE_RETURNS -> if (opcode == Opcodes.ICONST_1) Opcodes.ICONST_0 else opcode
            MutationOperator.FALSE_RETURNS -> if (opcode == Opcodes.ICONST_0) Opcodes.ICONST_1 else opcode
            else -> opcode
        }
    }

    private fun applyReturnMutation(opcode: Int) {
        val returnType = Type.getReturnType(methodDescriptor)
        when (targetMutation.operator) {
            MutationOperator.RETURN_VALS -> {
                // Pop original return value first, then push replacement
                when (opcode) {
                    Opcodes.IRETURN -> {
                        mv.visitInsn(Opcodes.POP)
                        mv.visitInsn(Opcodes.ICONST_0)
                        mv.visitInsn(Opcodes.IRETURN)
                    }
                    Opcodes.LRETURN -> {
                        mv.visitInsn(Opcodes.POP2)
                        mv.visitInsn(Opcodes.LCONST_0)
                        mv.visitInsn(Opcodes.LRETURN)
                    }
                    Opcodes.FRETURN -> {
                        mv.visitInsn(Opcodes.POP)
                        mv.visitInsn(Opcodes.FCONST_0)
                        mv.visitInsn(Opcodes.FRETURN)
                    }
                    Opcodes.DRETURN -> {
                        mv.visitInsn(Opcodes.POP2)
                        mv.visitInsn(Opcodes.DCONST_0)
                        mv.visitInsn(Opcodes.DRETURN)
                    }
                    Opcodes.ARETURN -> {
                        mv.visitInsn(Opcodes.POP)
                        mv.visitInsn(Opcodes.ACONST_NULL)
                        mv.visitInsn(Opcodes.ARETURN)
                    }
                    Opcodes.RETURN -> mv.visitInsn(Opcodes.RETURN)
                }
            }
            MutationOperator.NULL_RETURNS -> {
                if (opcode == Opcodes.ARETURN) {
                    mv.visitInsn(Opcodes.POP) // Pop original ref
                    mv.visitInsn(Opcodes.ACONST_NULL)
                    mv.visitInsn(Opcodes.ARETURN)
                } else {
                    super.visitInsn(opcode)
                }
            }
            MutationOperator.EMPTY_RETURNS -> {
                if (opcode == Opcodes.ARETURN && ReturnValueMutator.isCollectionOrArrayStatic(returnType)) {
                    mv.visitInsn(Opcodes.POP) // Pop original ref
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
                if (elementType.sort >= Type.BOOLEAN && elementType.sort <= Type.DOUBLE) {
                    // Primitive array: use NEWARRAY
                    mv.visitInsn(Opcodes.ICONST_0)
                    val newarrayOp =
                        when (elementType.sort) {
                            Type.BOOLEAN -> Opcodes.T_BOOLEAN
                            Type.BYTE -> Opcodes.T_BYTE
                            Type.CHAR -> Opcodes.T_CHAR
                            Type.SHORT -> Opcodes.T_SHORT
                            Type.INT -> Opcodes.T_INT
                            Type.LONG -> Opcodes.T_LONG
                            Type.FLOAT -> Opcodes.T_FLOAT
                            Type.DOUBLE -> Opcodes.T_DOUBLE
                            else -> Opcodes.T_INT
                        }
                    mv.visitIntInsn(Opcodes.NEWARRAY, newarrayOp)
                } else {
                    // Object array: use ANEWARRAY
                    mv.visitInsn(Opcodes.ICONST_0)
                    mv.visitTypeInsn(Opcodes.ANEWARRAY, elementType.internalName)
                }
                mv.visitInsn(Opcodes.ARETURN)
            }
            className == "java.util.List" || className == "kotlin.collections.List" ||
                className == "kotlin.collections.MutableList" -> {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Collections", "emptyList", "()Ljava/util/List;", false)
                mv.visitInsn(Opcodes.ARETURN)
            }
            className == "java.util.Set" || className == "kotlin.collections.Set" ||
                className == "kotlin.collections.MutableSet" -> {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Collections", "emptySet", "()Ljava/util/Set;", false)
                mv.visitInsn(Opcodes.ARETURN)
            }
            className == "java.util.Map" || className == "kotlin.collections.Map" ||
                className == "kotlin.collections.MutableMap" -> {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Collections", "emptyMap", "()Ljava/util/Map;", false)
                mv.visitInsn(Opcodes.ARETURN)
            }
            else -> mv.visitInsn(Opcodes.ARETURN)
        }
    }
}

// Static helper functions for mutation logic
internal object ConditionalMutator {
    fun mutateBoundaryStatic(opcode: Int): Int =
        when (opcode) {
            Opcodes.IFGE -> Opcodes.IFGT
            Opcodes.IFGT -> Opcodes.IFGE
            Opcodes.IFLE -> Opcodes.IFLT
            Opcodes.IFLT -> Opcodes.IFLE
            Opcodes.IF_ICMPGE -> Opcodes.IF_ICMPGT
            Opcodes.IF_ICMPGT -> Opcodes.IF_ICMPGE
            Opcodes.IF_ICMPLE -> Opcodes.IF_ICMPLT
            Opcodes.IF_ICMPLT -> Opcodes.IF_ICMPLE
            // Note: IF_ACMPEQ/IF_ACMPNE are removed - those are negations, not boundaries
            // (reference equality has no boundary concept like < vs <=)
            else -> opcode
        }

    fun mutateNegateStatic(opcode: Int): Int =
        when (opcode) {
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

internal object ArithmeticMutator {
    fun mutateStatic(opcode: Int): Int =
        when (opcode) {
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
            Opcodes.INEG -> Opcodes.INEG // Skip unary negation — stack-unsafe to replace with NOP
            Opcodes.LNEG -> Opcodes.LNEG
            Opcodes.FNEG -> Opcodes.FNEG
            Opcodes.DNEG -> Opcodes.DNEG
            else -> opcode
        }
}

internal object ReturnValueMutator {
    fun isCollectionOrArrayStatic(type: Type): Boolean {
        if (type.sort == Type.ARRAY) return true
        val cn = type.className
        // Type.className returns dotted form (java.util.List), not slashed.
        // Match against dotted forms and use prefix matching for packages.
        return cn == "java.util.List" ||
            cn == "java.util.ArrayList" ||
            cn == "java.util.LinkedList" ||
            cn == "java.util.Set" ||
            cn == "java.util.HashSet" ||
            cn == "java.util.TreeSet" ||
            cn == "java.util.Collection" ||
            cn == "java.util.Map" ||
            cn == "java.util.HashMap" ||
            cn == "java.util.TreeMap" ||
            cn.startsWith("kotlin.collections.")
    }
}

/**
 * Check if a Kotlin method is synthetic (generated by compiler).
 * Uses precise pattern matching to avoid false positives on user methods.
 */
internal fun isKotlinSyntheticMethod(name: String): Boolean {
    return name == "copy\$default" ||
        name.startsWith("component") && name.endsWith("\$default") ||
        name.endsWith("\$serializer") ||
        name == "<init>\$default" ||
        name == "toString\$default" ||
        name == "hashCode\$default" ||
        name == "equals\$default"
}
