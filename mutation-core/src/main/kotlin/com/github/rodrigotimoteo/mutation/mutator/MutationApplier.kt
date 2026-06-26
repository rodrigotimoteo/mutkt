package com.github.rodrigotimoteo.mutation.mutator

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Dotted class names recognized as collections/arrays by EMPTY_RETURNS.
 * Java + Kotlin collection interfaces. Used to pick a replacement empty
 * instance (e.g. [java.util.Collections.emptyList]) via [COLLECTION_EMPTY_METHOD].
 */
private val COLLECTION_TYPES: Set<String> =
    setOf(
        "java.util.List",
        "kotlin.collections.List",
        "kotlin.collections.MutableList",
        "java.util.Set",
        "kotlin.collections.Set",
        "kotlin.collections.MutableSet",
        "java.util.Map",
        "kotlin.collections.Map",
        "kotlin.collections.MutableMap",
    )

/**
 * Maps a collection interface short name to its [java.util.Collections] factory.
 * Used by [MutationApplierMethodVisitor.applyEmptyReturn] to emit the right
 * INVOKESTATIC call when the return type is in [COLLECTION_TYPES].
 */
private val COLLECTION_EMPTY_METHOD: Map<String, String> =
    mapOf(
        "List" to "emptyList",
        "Set" to "emptySet",
        "Map" to "emptyMap",
    )

/**
 * ClassVisitor that applies a specific mutation.
 */
internal class MutationApplierVisitor(
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
internal class MutationApplierMethodVisitor(
    private val mv: MethodVisitor,
    private val className: String,
    private val methodName: String,
    private val methodDescriptor: String,
    private val targetMutation: MutationInfo,
    private val enabledOperators: Set<MutationOperator>,
) : MethodVisitor(Opcodes.ASM9, mv) {
    private var currentLineNumber = -1
    private var applied = false

    // Per-(operatorName, line, method) occurrence counter for matching the
    // scanner's per-(operator, line) occurrence index. The applier mirrors the
    // scanner's candidate predicate so identical instructions on the same line
    // are matched positionally rather than all hitting the first occurrence.
    private val occurrenceCounters = mutableMapOf<Triple<String, Int, String>, Int>()

    // Mirrors the scanner's instanceofCount so SEALED_WHEN via instanceof only
    // matches 2nd+ INSTANCEOF on a line (the 1st is intentionally skipped).
    private var instanceofCount = 0

    /**
     * If [isCandidate] is true, advance the running counter for the target's
     * (operator, line, method) triple and return true when the current index
     * equals the target's expected occurrenceIndex. Returns false otherwise.
     */
    private fun checkAndCountOccurrence(isCandidate: Boolean): Boolean {
        if (!isCandidate) return false
        val key =
            Triple(
                targetMutation.operator.operatorName,
                targetMutation.lineNumber,
                methodName,
            )
        val target = targetMutation.metadata["occurrenceIndex"]?.toIntOrNull() ?: 0
        val current = occurrenceCounters.getOrDefault(key, 0)
        occurrenceCounters[key] = current + 1
        return current == target
    }

    /**
     * True when [opcode] is an instruction the target operator could mutate
     * inside visitInsn. Mirrors the scanner's per-operator candidate checks
     * so non-mutating opcodes (e.g. NOP) don't consume occurrence indices.
     */
    private fun isInsnCandidate(opcode: Int): Boolean {
        return when (targetMutation.operator) {
            MutationOperator.ARITHMETIC -> ArithmeticMutator.mutateStatic(opcode) != opcode
            MutationOperator.RETURN_VALS ->
                opcode == Opcodes.IRETURN || opcode == Opcodes.LRETURN ||
                    opcode == Opcodes.FRETURN || opcode == Opcodes.DRETURN ||
                    opcode == Opcodes.ARETURN
            MutationOperator.NULL_RETURNS, MutationOperator.EMPTY_RETURNS -> opcode == Opcodes.ARETURN
            MutationOperator.TRUE_RETURNS -> opcode == Opcodes.ICONST_1
            MutationOperator.FALSE_RETURNS -> opcode == Opcodes.ICONST_0
            else -> false
        }
    }

    private fun isJumpInsnCandidate(opcode: Int): Boolean {
        return when (targetMutation.operator) {
            MutationOperator.CONDITIONALS_BOUNDARY -> ConditionalMutator.mutateBoundaryStatic(opcode) != opcode
            MutationOperator.NEGATE_CONDITIONALS -> ConditionalMutator.mutateNegateStatic(opcode) != opcode
            MutationOperator.NULL_SAFETY -> opcode == Opcodes.IFNULL || opcode == Opcodes.IFNONNULL
            else -> false
        }
    }

    private fun isIincCandidate(increment: Int): Boolean {
        return when (targetMutation.operator) {
            MutationOperator.ARITHMETIC, MutationOperator.INCREMENTS -> increment != 0
            else -> false
        }
    }

    private fun isMethodCallCandidate(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
    ): Boolean {
        val isInvoke =
            opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKESTATIC ||
                opcode == Opcodes.INVOKEINTERFACE || opcode == Opcodes.INVOKESPECIAL
        if (!isInvoke) return false
        return when (targetMutation.operator) {
            MutationOperator.VOID_METHOD_CALLS -> {
                val returnType = Type.getReturnType(descriptor)
                returnType.sort == Type.VOID && name != "<init>" && name != "<clinit>"
            }
            MutationOperator.CONSTRUCTOR_CALLS -> name == "<init>"
            MutationOperator.NON_VOID_METHOD_CALLS -> {
                val returnType = Type.getReturnType(descriptor)
                returnType.sort != Type.VOID && name != "<init>" && name != "<clinit>"
            }
            MutationOperator.DATA_CLASS_COPY ->
                (opcode == Opcodes.INVOKESPECIAL || opcode == Opcodes.INVOKEVIRTUAL) && name == "copy" ||
                    (opcode == Opcodes.INVOKESTATIC && name == "copy\$default")
            MutationOperator.COROUTINE ->
                (
                    opcode == Opcodes.INVOKESTATIC &&
                        (
                            owner == "kotlinx/coroutines/BuildersKt" ||
                                owner == "kotlinx/coroutines/JobKt" ||
                                owner == "kotlinx/coroutines/GlobalScope"
                        )
                ) ||
                    descriptor.endsWith("Lkotlin/coroutines/Continuation;") ||
                    (
                        name == "getCOROUTINE_SUSPENDED" &&
                            (
                                owner == "kotlin/coroutines/intrinsics/IntrinsicsKt" ||
                                    owner == "kotlin/jvm/internal/Intrinsics"
                            )
                    )
            MutationOperator.NULL_SAFETY ->
                opcode == Opcodes.INVOKESTATIC &&
                    owner == "kotlin/jvm/internal/Intrinsics" &&
                    (
                        name == "checkNotNull" || name == "checkNotNullParameter" ||
                            name == "checkNotNullExpressionValue" ||
                            name == "throwUninitializedPropertyAccessException"
                    )
            else -> false
        }
    }

    override fun visitLineNumber(
        line: Int,
        start: org.objectweb.asm.Label?,
    ) {
        currentLineNumber = line
        instanceofCount = 0
        super.visitLineNumber(line, start)
    }

    override fun visitInsn(opcode: Int) {
        if (!applied && currentLineNumber == targetMutation.lineNumber &&
            methodName == targetMutation.methodName &&
            methodDescriptor == targetMutation.methodDescriptor &&
            opcode == targetMutation.originalOpcode &&
            checkAndCountOccurrence(isInsnCandidate(opcode))
        ) {
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
        if (!applied && currentLineNumber == targetMutation.lineNumber &&
            methodName == targetMutation.methodName &&
            methodDescriptor == targetMutation.methodDescriptor &&
            opcode == targetMutation.originalOpcode &&
            checkAndCountOccurrence(isJumpInsnCandidate(opcode))
        ) {
            // NULL_SAFETY mutations: getMutatedOpcode has no NULL_SAFETY case,
            // so the scanner-emitted targetMutation.mutatedOpcode is the source
            // of truth. Fall back to it whenever the helper returns the input
            // unchanged (i.e. no rule for this opcode under the current operator).
            val mutatedOpcode = getMutatedOpcode(opcode)
            val effectiveOpcode =
                if (mutatedOpcode == opcode) targetMutation.mutatedOpcode else mutatedOpcode
            if (effectiveOpcode != opcode) {
                super.visitJumpInsn(effectiveOpcode, label)
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
        if (opcode == Opcodes.INSTANCEOF) {
            instanceofCount++
        }
        if (!applied && currentLineNumber == targetMutation.lineNumber &&
            methodName == targetMutation.methodName &&
            methodDescriptor == targetMutation.methodDescriptor &&
            opcode == targetMutation.originalOpcode &&
            targetMutation.operator == MutationOperator.SEALED_WHEN &&
            // Mirror scanner rule: only count/mutate 2nd+ instanceof on a line
            checkAndCountOccurrence(opcode == Opcodes.INSTANCEOF && instanceofCount >= 2)
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
        if (!applied && currentLineNumber == targetMutation.lineNumber &&
            methodName == targetMutation.methodName &&
            methodDescriptor == targetMutation.methodDescriptor &&
            checkAndCountOccurrence(isIincCandidate(increment))
        ) {
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
            currentLineNumber == targetMutation.lineNumber &&
            methodName == targetMutation.methodName &&
            methodDescriptor == targetMutation.methodDescriptor &&
            checkAndCountOccurrence(true)
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
            currentLineNumber == targetMutation.lineNumber &&
            methodName == targetMutation.methodName &&
            methodDescriptor == targetMutation.methodDescriptor &&
            checkAndCountOccurrence(true)
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
        if (!applied && currentLineNumber == targetMutation.lineNumber &&
            methodName == targetMutation.methodName &&
            methodDescriptor == targetMutation.methodDescriptor &&
            checkAndCountOccurrence(isMethodCallCandidate(opcode, owner, name, descriptor))
        ) {
            val didApply =
                when (targetMutation.operator) {
                    MutationOperator.VOID_METHOD_CALLS ->
                        applyVoidMethodCall(mv, opcode, owner, name, descriptor)
                    MutationOperator.CONSTRUCTOR_CALLS ->
                        applyConstructorCall(mv, opcode, owner, name, descriptor)
                    MutationOperator.NON_VOID_METHOD_CALLS ->
                        applyNonVoidMethodCall(mv, opcode, owner, name, descriptor)
                    MutationOperator.DATA_CLASS_COPY ->
                        applyDataClassCopy(mv, opcode, owner, name, descriptor)
                    MutationOperator.COROUTINE ->
                        applyCoroutine(mv, opcode, owner, name, descriptor)
                    MutationOperator.NULL_SAFETY ->
                        applyNullSafety(mv, opcode, owner, name, descriptor)
                    else -> false
                }
            if (didApply) {
                applied = true
                return
            }
        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
    }

    private fun applyVoidMethodCall(
        mv: MethodVisitor,
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
    ): Boolean {
        if (opcode != Opcodes.INVOKEVIRTUAL && opcode != Opcodes.INVOKESTATIC &&
            opcode != Opcodes.INVOKEINTERFACE && opcode != Opcodes.INVOKESPECIAL
        ) {
            return false
        }
        val returnType = Type.getReturnType(descriptor)
        if (returnType.sort != Type.VOID) return false
        popArgs(Type.getArgumentTypes(descriptor))
        if (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE ||
            opcode == Opcodes.INVOKESPECIAL
        ) {
            mv.visitInsn(Opcodes.POP) // Pop receiver
        }
        return true
    }

    private fun applyConstructorCall(
        mv: MethodVisitor,
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
    ): Boolean {
        if (opcode != Opcodes.INVOKESPECIAL || name != "<init>") return false
        // Stack: [uninitialized_ref, uninitialized_ref, arg1, ..., argN]
        // NEW+DUP created 2 refs. popArgs removes args. POP2 removes both refs.
        popArgs(Type.getArgumentTypes(descriptor))
        mv.visitInsn(Opcodes.POP2) // Pop both NEW+DUP refs
        mv.visitInsn(Opcodes.ACONST_NULL) // Push null as replacement
        return true
    }

    private fun applyNonVoidMethodCall(
        mv: MethodVisitor,
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
    ): Boolean {
        val returnType = Type.getReturnType(descriptor)
        if (returnType.sort == Type.VOID) return false
        popArgs(Type.getArgumentTypes(descriptor))
        if (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE ||
            opcode == Opcodes.INVOKESPECIAL
        ) {
            mv.visitInsn(Opcodes.POP) // Pop receiver for virtual/interface/special calls
        }
        pushDefaultValue(returnType)
        return true
    }

    private fun applyDataClassCopy(
        mv: MethodVisitor,
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
    ): Boolean {
        if ((opcode == Opcodes.INVOKESPECIAL || opcode == Opcodes.INVOKEVIRTUAL) && name == "copy") {
            // Remove copy() call: pop args + receiver, push null
            popArgs(Type.getArgumentTypes(descriptor))
            mv.visitInsn(Opcodes.POP) // Pop receiver
            mv.visitInsn(Opcodes.ACONST_NULL)
            return true
        }
        if (opcode == Opcodes.INVOKESTATIC && name == "copy\$default") {
            // copy$default is static: (this, ...params, bitmask) -> instance
            // pop all args and push null as replacement
            popArgs(Type.getArgumentTypes(descriptor))
            mv.visitInsn(Opcodes.ACONST_NULL)
            return true
        }
        return false
    }

    private fun applyCoroutine(
        mv: MethodVisitor,
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
    ): Boolean {
        // Static calls (e.g. BuildersKt.runBlocking, getCOROUTINE_SUSPENDED) have
        // no receiver on the stack. Virtual/interface suspend calls do, so pop it
        // before clearing args.
        popArgs(Type.getArgumentTypes(descriptor))
        if (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE ||
            opcode == Opcodes.INVOKESPECIAL
        ) {
            mv.visitInsn(Opcodes.POP)
        }
        pushDefaultValue(Type.getReturnType(descriptor))
        return true
    }

    private fun applyNullSafety(
        mv: MethodVisitor,
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
    ): Boolean {
        // Remove null check call (static — no receiver on stack)
        // checkNotNull(Object) returns the checked value (non-void)
        // checkNotNullParameter(Object, String) returns void
        // throwUninitializedPropertyAccessException(String) returns void
        popArgs(Type.getArgumentTypes(descriptor))
        val returnType = Type.getReturnType(descriptor)
        if (returnType.sort != Type.VOID) {
            pushDefaultValue(returnType)
        }
        return true
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
            className in COLLECTION_TYPES -> {
                // Pick the empty factory method by matching the short
                // interface name (List/Set/Map) to COLLECTION_EMPTY_METHOD.
                // The match must also accept Kotlin's "Mutable*" prefix —
                // `kotlin.collections.MutableList` doesn't end with `.List`
                // so a naive `endsWith(".List")` lookup throws NoSuchElement.
                // We resolve by stripping the `Mutable` prefix first, then
                // falling back to the original last-segment match for the
                // non-Mutable interfaces.
                val lastSegment = className.substringAfterLast('.')
                val normalized =
                    if (lastSegment.startsWith("Mutable")) {
                        lastSegment.removePrefix("Mutable")
                    } else {
                        lastSegment
                    }
                val shortName =
                    COLLECTION_EMPTY_METHOD.keys.firstOrNull { it == normalized }
                        ?: COLLECTION_EMPTY_METHOD.keys.first { className.endsWith(".$it") }
                val emptyMethod = COLLECTION_EMPTY_METHOD.getValue(shortName)
                val internalName = className.replace('.', '/')
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "java/util/Collections",
                    emptyMethod,
                    "()L$internalName;",
                    false,
                )
                mv.visitInsn(Opcodes.ARETURN)
            }
            else -> mv.visitInsn(Opcodes.ARETURN)
        }
    }
}
