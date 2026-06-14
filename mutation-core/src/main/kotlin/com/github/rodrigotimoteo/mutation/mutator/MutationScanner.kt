package com.github.rodrigotimoteo.mutation.mutator

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * ClassVisitor that scans for mutation points.
 */
internal class MutationScannerVisitor(
    private val mutations: MutableList<MutationInfo>,
    private val enabledOperators: Set<MutationOperator>,
    private val excludedMethods: Set<String> = emptySet(),
) : ClassVisitor(Opcodes.ASM9) {
    private var currentClassName = ""
    private var isKotlinClass = false
    private var classSuppressed = false
    private var suppressedOperators = emptySet<String>()
    private var currentMethodName = ""

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
        if (isKotlinClass && isKotlinSyntheticMethod(name)) return null

        // Skip excluded methods
        if (excludedMethods.any { name.contains(it) }) return null

        currentMethodName = name

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
}

/**
 * MethodVisitor that scans for mutation points.
 */
internal class MutationScannerMethodVisitor(
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
    private var previousOpcode = -1

    // Per-(operatorName, line) occurrence counter so the applier can disambiguate
    // repeated identical instructions on the same source line.
    private val occurrenceCounters = mutableMapOf<Pair<String, Int>, Int>()

    // Method-level @SuppressMutations state
    private var methodSuppressed = false
    private var methodSuppressedOperators: Set<String> = emptySet()

    /**
     * Allocate the next occurrence index for (operatorName, lineNumber). Each call
     * returns a unique increasing index, which is written to MutationInfo.metadata
     * so the applier can match the Nth occurrence of an instruction pattern.
     */
    private fun nextOccurrenceIndex(
        operatorName: String,
        line: Int,
    ): Int {
        val key = operatorName to line
        val current = occurrenceCounters.getOrDefault(key, 0)
        occurrenceCounters[key] = current + 1
        return current
    }

    /**
     * Check if mutation should be suppressed.
     * Suppression priority: class-level > method-level (both checked independently).
     */
    private fun isSuppressed(operator: MutationOperator): Boolean {
        if (classSuppressed) return true
        if (suppressedOperators.contains(operator.operatorName)) return true
        if (methodSuppressed) return true
        if (methodSuppressedOperators.contains(operator.operatorName)) return true
        return false
    }

    override fun visitAnnotation(
        desc: String,
        visible: Boolean,
    ): org.objectweb.asm.AnnotationVisitor? {
        if (desc == "Lcom/github/rodrigotimoteo/mutation/annotation/SuppressMutations;") {
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
                                    methodSuppressedOperators = methodSuppressedOperators + value
                                    hasExplicitOperators = true
                                }
                            }
                        }
                    }
                    return super.visitArray(name)
                }

                override fun visitEnd() {
                    super.visitEnd()
                    if (!hasExplicitOperators) {
                        methodSuppressed = true
                    }
                }
            }
        }
        return super.visitAnnotation(desc, visible)
    }

    private fun tryAddMutation(mutation: MutationInfo) {
        if (!isSuppressed(mutation.operator)) {
            val enriched =
                if (mutation.metadata.containsKey("occurrenceIndex")) {
                    mutation
                } else {
                    val idx =
                        nextOccurrenceIndex(
                            mutation.operator.operatorName,
                            currentLineNumber,
                        )
                    mutation.copy(
                        metadata = mutation.metadata + ("occurrenceIndex" to idx.toString()),
                    )
                }
            mutations.add(enriched)
        }
    }

    override fun visitLineNumber(
        line: Int,
        start: org.objectweb.asm.Label?,
    ) {
        currentLineNumber = line
        instanceofCount = 0 // Reset per line — SEALED_WHEN chains are per-line
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
        checkIincMutations(increment)
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
     * Dispatches to per-operator scanners.
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
        checkDataClassCopy(opcode, owner, name, descriptor)
        checkCoroutine(opcode, owner, name, descriptor)
        checkNullSafety(opcode, owner, name, descriptor)
    }

    /**
     * DATA_CLASS_COPY: call to copy() or copy$default() method (data class generated).
     * Kotlin data classes use INVOKEVIRTUAL for copy() and INVOKESTATIC for copy$default().
     */
    private fun checkDataClassCopy(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
    ) {
        if (MutationOperator.DATA_CLASS_COPY !in enabledOperators) return
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

    /**
     * COROUTINE: detect suspend markers, coroutine builders.
     */
    private fun checkCoroutine(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
    ) {
        if (MutationOperator.COROUTINE !in enabledOperators) return
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

    /**
     * NULL_SAFETY: detect Kotlin null check intrinsics.
     */
    private fun checkNullSafety(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
    ) {
        if (MutationOperator.NULL_SAFETY !in enabledOperators) return
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
                    // Mutation: replace ifnull with nop (fall through to call → NPE if null)
                    tryAddMutation(
                        MutationInfo(
                            operator = MutationOperator.NULL_SAFETY,
                            className = className,
                            methodName = methodName,
                            methodDescriptor = methodDescriptor,
                            lineNumber = currentLineNumber,
                            description = "Remove null check: ?.",
                            originalOpcode = Opcodes.IFNULL,
                            mutatedOpcode = Opcodes.NOP,
                        ),
                    )
                }
                Opcodes.IFNONNULL -> {
                    // ?: elvis: ifnonnull skips the default value path
                    // Mutation: replace ifnonnull with nop (fall through to default → ignores value)
                    tryAddMutation(
                        MutationInfo(
                            operator = MutationOperator.NULL_SAFETY,
                            className = className,
                            methodName = methodName,
                            methodDescriptor = methodDescriptor,
                            lineNumber = currentLineNumber,
                            description = "Remove null check: ?:",
                            originalOpcode = Opcodes.IFNONNULL,
                            mutatedOpcode = Opcodes.NOP,
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

    private fun checkIincMutations(increment: Int) {
        val mutated = -increment
        if (mutated == increment) return
        // ARITHMETIC takes precedence over INCREMENTS when both are enabled
        val operator =
            when {
                MutationOperator.ARITHMETIC in enabledOperators -> MutationOperator.ARITHMETIC
                MutationOperator.INCREMENTS in enabledOperators -> MutationOperator.INCREMENTS
                else -> return
            }
        tryAddMutation(
            MutationInfo(
                operator = operator,
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
        // Only create mutations when we see IRETURN preceded by ICONST_1 or ICONST_0
        // This ensures we only mutate actual return values, not local variable assignments
        if (opcode != Opcodes.IRETURN) {
            previousOpcode = opcode
            return
        }
        val returnType = Type.getReturnType(methodDescriptor)
        if (returnType.sort != Type.BOOLEAN) {
            previousOpcode = opcode
            return
        }

        if (MutationOperator.TRUE_RETURNS in enabledOperators && previousOpcode == Opcodes.ICONST_1) {
            tryAddMutation(
                MutationInfo(
                    operator = MutationOperator.TRUE_RETURNS,
                    className = className,
                    methodName = methodName,
                    methodDescriptor = methodDescriptor,
                    lineNumber = currentLineNumber,
                    description = "Boolean return: true -> false",
                    originalOpcode = Opcodes.ICONST_1,
                    mutatedOpcode = Opcodes.ICONST_0,
                ),
            )
        }
        if (MutationOperator.FALSE_RETURNS in enabledOperators && previousOpcode == Opcodes.ICONST_0) {
            tryAddMutation(
                MutationInfo(
                    operator = MutationOperator.FALSE_RETURNS,
                    className = className,
                    methodName = methodName,
                    methodDescriptor = methodDescriptor,
                    lineNumber = currentLineNumber,
                    description = "Boolean return: false -> true",
                    originalOpcode = Opcodes.ICONST_0,
                    mutatedOpcode = Opcodes.ICONST_1,
                ),
            )
        }
        previousOpcode = opcode
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
