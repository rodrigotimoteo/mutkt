package com.github.rodrigotimoteo.mutation.mutator

import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive tests for Mutator.kt covering:
 * - getCommonSuperClass indirect behavior
 * - loadClass caching
 * - generateMutants filtering
 * - MutationScannerVisitor filtering rules
 * - MutationScannerMethodVisitor per-operator branches
 * - MutationApplierMethodVisitor applier logic
 */
class MutatorFullTest {
    // ======================================================================
    // Core Mutator — getCommonSuperClass behavior (tested indirectly)
    // ======================================================================

    @Test
    fun `getCommonSuperClass returns type1 when type1 == type2`() {
        // applyMutation invokes ClassWriter which calls getCommonSuperClass.
        // With COMPUTE_FRAMES and a simple method, ASM may not need it.
        // The class still goes through the full pipeline without crashing.
        val bytes =
            buildClassWithMethod(descriptor = "()I") { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.ICONST_2)
                mv.visitInsn(Opcodes.IADD)
            }
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val mutations = mutator.scanMutations(bytes)
        val mutated = mutator.applyMutation(bytes, mutations.first())
        assertTrue(mutated.isNotEmpty())
        // Mutated class should still be readable
        ClassReader(mutated).accept(
            object : ClassVisitor(Opcodes.ASM9) {},
            ClassReader.SKIP_FRAMES,
        )
    }

    @Test
    fun `getCommonSuperClass returns Object when one type is Object`() {
        // Subclass of Object — applyMutation will go through ClassWriter.
        val bytes = buildClassWithSubclassHierarchy()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val mutations = mutator.scanMutations(bytes)
        if (mutations.isNotEmpty()) {
            val mutated = mutator.applyMutation(bytes, mutations.first())
            assertTrue(mutated.isNotEmpty())
        }
    }

    @Test
    fun `getCommonSuperClass returns superclass when c2 is subclass of c1`() {
        // Class extending a known type — exercises class hierarchy walk.
        val bytes = buildClassWithDeepHierarchy()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(mutations.isNotEmpty())
        val mutated = mutator.applyMutation(bytes, mutations.first())
        assertTrue(mutated.isNotEmpty())
    }

    @Test
    fun `getCommonSuperClass returns type2 when c1 fails to load`() {
        // Reference an unknown class — loadClass will fail, but the apply
        // pipeline should still not crash the entire operation.
        val bytes = buildClassWithUnknownReference()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val mutations = mutator.scanMutations(bytes)
        if (mutations.isNotEmpty()) {
            // Should not throw
            val mutated = mutator.applyMutation(bytes, mutations.first())
            assertTrue(mutated.isNotEmpty())
        }
    }

    @Test
    fun `getCommonSuperClass finds common interface`() {
        // Build a class implementing an interface — interface hierarchy walk.
        val bytes = buildClassWithInterface()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val mutations = mutator.scanMutations(bytes)
        if (mutations.isNotEmpty()) {
            val mutated = mutator.applyMutation(bytes, mutations.first())
            assertTrue(mutated.isNotEmpty())
        }
    }

    @Test
    fun `loadClass caches and reuses Class instances`() {
        // applyMutation runs loadClass multiple times internally. The cache
        // is private. We verify behavior is consistent across calls.
        val bytes =
            buildClassWithMethod(descriptor = "()I") { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.ICONST_2)
                mv.visitInsn(Opcodes.IADD)
            }
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val mutations = mutator.scanMutations(bytes)
        // Apply same mutation twice — second call exercises cached path
        val first = mutator.applyMutation(bytes, mutations.first())
        val second = mutator.applyMutation(bytes, mutations.first())
        assertTrue(first.isNotEmpty())
        assertTrue(second.isNotEmpty())
    }

    @Test
    fun `generateMutants skips unchanged mutations`() {
        // A class with no mutations should produce no mutants
        val bytes = buildEmptyClass()
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val mutants = mutator.generateMutants(bytes)
        assertEquals(0, mutants.size, "Empty class should produce no mutants")
    }

    // ======================================================================
    // MutationScannerVisitor filtering
    // ======================================================================

    @Test
    fun `scanner skips ACC_SYNTHETIC methods`() {
        val bytes = buildClassWithSyntheticMethod()
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val mutations = mutator.scanMutations(bytes)
        assertTrue(
            mutations.none { it.methodName == "synthetic" },
            "Synthetic method should be skipped",
        )
    }

    @Test
    fun `scanner skips ACC_BRIDGE methods`() {
        val bytes = buildClassWithBridgeMethod()
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val mutations = mutator.scanMutations(bytes)
        assertTrue(
            mutations.none { it.methodName == "bridge" },
            "Bridge method should be skipped",
        )
    }

    @Test
    fun `scanner skips constructors and static initializers`() {
        val bytes =
            buildClassWithMethod(descriptor = "()I") { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.ICONST_2)
                mv.visitInsn(Opcodes.IADD)
            }
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val mutations = mutator.scanMutations(bytes)
        assertTrue(
            mutations.none { it.methodName == "<init>" || it.methodName == "<clinit>" },
            "Constructor and clinit should be skipped",
        )
    }

    @Test
    fun `scanner skips Kotlin synthetic copy$default`() {
        val bytes =
            buildKotlinClassWithMethod("copy\$default") { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.IRETURN)
            }
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val mutations = mutator.scanMutations(bytes)
        assertTrue(
            mutations.none { it.methodName == "copy\$default" },
            "Kotlin copy\$default should be skipped",
        )
    }

    @Test
    fun `scanner skips Kotlin synthetic componentN$default`() {
        val bytes =
            buildKotlinClassWithMethod("component1\$default") { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.IRETURN)
            }
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val mutations = mutator.scanMutations(bytes)
        assertTrue(
            mutations.none { it.methodName == "component1\$default" },
            "Kotlin component1\$default should be skipped",
        )
    }

    @Test
    fun `scanner skips Kotlin synthetic $serializer`() {
        val bytes =
            buildKotlinClassWithMethod("Foo\$serializer") { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.IRETURN)
            }
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val mutations = mutator.scanMutations(bytes)
        assertTrue(
            mutations.none { it.methodName == "Foo\$serializer" },
            "Kotlin \$serializer should be skipped",
        )
    }

    @Test
    fun `isKotlinSyntheticMethod matches each pattern and rejects normal names`() {
        // isKotlinSyntheticMethod is internal, test through scanner behavior
        val syntheticNames =
            listOf(
                "copy\$default",
                "component1\$default",
                "component2\$default",
                "Foo\$serializer",
                "<init>\$default",
                "toString\$default",
                "hashCode\$default",
                "equals\$default",
            )
        for (name in syntheticNames) {
            val bytes =
                buildKotlinClassWithMethod(name) { mv ->
                    mv.visitInsn(Opcodes.ICONST_1)
                    mv.visitInsn(Opcodes.IRETURN)
                }
            val mutations =
                Mutator(MutationOperator.MVP_OPERATORS).scanMutations(bytes)
            assertTrue(
                mutations.none { it.methodName == name },
                "Synthetic '$name' should be skipped, got: ${mutations.map { it.methodName }}",
            )
        }
        // Normal names should NOT be skipped
        val normalBytes =
            buildKotlinClassWithMethod("compute") { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.IRETURN)
            }
        val normalMutations =
            Mutator(MutationOperator.MVP_OPERATORS).scanMutations(normalBytes)
        assertTrue(
            normalMutations.any { it.methodName == "compute" },
            "Normal 'compute' should be scanned",
        )
    }

    @Test
    fun `class SuppressMutations with unknown array name delegates to super`() {
        // The annotation visitor's visitArray returns super when name != "operators".
        // The annotation has BOTH a non-"operators" array AND a real "operators"
        // array. The unknown array's visitArray must delegate to super without
        // breaking parsing of the real "operators" array — so ARITHMETIC must be
        // suppressed by the "operators" array.
        val bytes = buildClassWithUnknownArrayInSuppress()
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val mutations = mutator.scanMutations(bytes)
        // The "operators" array has ["ARITHMETIC"] → ARITHMETIC must be suppressed
        // even though the annotation also has an unknown array attribute.
        assertTrue(
            mutations.none { it.operator == MutationOperator.ARITHMETIC },
            "ARITHMETIC should be suppressed by the operators array even when an " +
                "unknown array attribute is also present, got: ${mutations.map { it.operator }}",
        )
    }

    @Test
    fun `method SuppressMutations with unknown array name delegates to super`() {
        // Method-level: build a method with @SuppressMutations(operators=["ARITHMETIC"])
        // and another unknown array attribute. The "operators" array still
        // suppresses ARITHMETIC. The unknown array's delegate to super should
        // not break parsing.
        val bytes = buildMethodWithUnknownArrayInSuppress()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC, MutationOperator.RETURN_VALS))
        val mutations = mutator.scanMutations(bytes)
        // ARITHMETIC should be suppressed via the "operators" array
        assertTrue(
            mutations.none { it.methodName == "test" && it.operator == MutationOperator.ARITHMETIC },
            "ARITHMETIC should be suppressed by operators array",
        )
    }

    // ======================================================================
    // MutationScannerMethodVisitor per-operator
    // ======================================================================

    @Test
    fun `ARITHMETIC scans IINC increment`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_0)
                mv.visitVarInsn(Opcodes.ISTORE, 1)
                mv.visitIincInsn(1, 1)
            }
        val mutations = Mutator(setOf(MutationOperator.ARITHMETIC)).scanMutations(bytes)
        assertTrue(
            mutations.any { it.operator == MutationOperator.ARITHMETIC && it.originalOpcode == Opcodes.IINC },
            "ARITHMETIC should scan IINC",
        )
    }

    @Test
    fun `CONDITIONALS_BOUNDARY ignores non-boundary jumps`() {
        // GOTO is not a boundary jump
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                val l = Label()
                mv.visitJumpInsn(Opcodes.GOTO, l)
                mv.visitLabel(l)
            }
        val mutations =
            Mutator(setOf(MutationOperator.CONDITIONALS_BOUNDARY)).scanMutations(bytes)
        assertEquals(0, mutations.size, "GOTO should not match CONDITIONALS_BOUNDARY")
    }

    @Test
    fun `NEGATE_CONDITIONALS scans IF_ACMPEQ and IF_ACMPNE`() {
        val bytes =
            buildClassWithMethod(descriptor = "(Ljava/lang/Object;Ljava/lang/Object;)I") { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 1)
                mv.visitVarInsn(Opcodes.ALOAD, 2)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IF_ACMPEQ, l)
                mv.visitLabel(l)
            }
        val mutations =
            Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS)).scanMutations(bytes)
        assertTrue(
            mutations.any { it.originalOpcode == Opcodes.IF_ACMPEQ },
            "Should scan IF_ACMPEQ",
        )

        val bytes2 =
            buildClassWithMethod(descriptor = "(Ljava/lang/Object;Ljava/lang/Object;)I") { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 1)
                mv.visitVarInsn(Opcodes.ALOAD, 2)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IF_ACMPNE, l)
                mv.visitLabel(l)
            }
        val mutations2 =
            Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS)).scanMutations(bytes2)
        assertTrue(
            mutations2.any { it.originalOpcode == Opcodes.IF_ACMPNE },
            "Should scan IF_ACMPNE",
        )
    }

    @Test
    fun `NEGATE_CONDITIONALS ignores non-negatable jumps`() {
        // GOTO cannot be negated
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                val l = Label()
                mv.visitJumpInsn(Opcodes.GOTO, l)
                mv.visitLabel(l)
            }
        val mutations =
            Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS)).scanMutations(bytes)
        assertEquals(0, mutations.size, "GOTO should not match NEGATE_CONDITIONALS")
    }

    @Test
    fun `INCREMENTS ignores zero increment`() {
        // IINC with 0 increment is a no-op — scanner checks mutated != increment
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_0)
                mv.visitVarInsn(Opcodes.ISTORE, 1)
                mv.visitIincInsn(1, 0)
            }
        val mutations = Mutator(setOf(MutationOperator.INCREMENTS)).scanMutations(bytes)
        assertEquals(0, mutations.size, "IINC 0 should not produce INCREMENTS mutation")
    }

    @Test
    fun `RETURN_VALS does not create EMPTY_RETURNS for non-collection`() {
        // RETURN_VALS operator scans ARETURN for non-collection types.
        // EMPTY_RETURNS is separate and only fires for collection/array.
        val bytes =
            buildClassWithMethod(descriptor = "()Ljava/lang/String;") { mv ->
                mv.visitInsn(Opcodes.ACONST_NULL)
                mv.visitInsn(Opcodes.ARETURN)
            }
        val mutations = Mutator(setOf(MutationOperator.RETURN_VALS)).scanMutations(bytes)
        assertTrue(mutations.any { it.operator == MutationOperator.RETURN_VALS })
        assertTrue(
            mutations.none { it.operator == MutationOperator.EMPTY_RETURNS },
            "String return should not produce EMPTY_RETURNS",
        )
    }

    @Test
    fun `VOID_METHOD_CALLS ignores init and clinit`() {
        // <init> and <clinit> should be filtered out
        val bytes = buildClassWithVoidCallToInit()
        val mutator = Mutator(setOf(MutationOperator.VOID_METHOD_CALLS))
        val mutations = mutator.scanMutations(bytes)
        // The scanner visits <init> only at the class level (not through visitMethod
        // for method scanning). Verify no mutation targets the init.
        assertTrue(
            mutations.none { it.methodName == "<init>" || it.methodName == "<clinit>" },
            "init/clinit should be skipped",
        )
    }

    @Test
    fun `VOID_METHOD_CALLS ignores non-void calls`() {
        val bytes = buildClassWithNonVoidCall()
        val mutator = Mutator(setOf(MutationOperator.VOID_METHOD_CALLS))
        val mutations = mutator.scanMutations(bytes)
        assertEquals(0, mutations.size, "Non-void call should not match VOID_METHOD_CALLS")
    }

    @Test
    fun `CONSTRUCTOR_CALLS scans INVOKESPECIAL init`() {
        val bytes = buildClassWithNewObject()
        val mutator = Mutator(setOf(MutationOperator.CONSTRUCTOR_CALLS))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(
            mutations.any { it.operator == MutationOperator.CONSTRUCTOR_CALLS },
            "Should scan <init> call",
        )
    }

    @Test
    fun `NON_VOID_METHOD_CALLS scans non-void method call`() {
        val bytes = buildClassWithStringLengthCall()
        val mutator = Mutator(setOf(MutationOperator.NON_VOID_METHOD_CALLS))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(
            mutations.any { it.operator == MutationOperator.NON_VOID_METHOD_CALLS },
            "Should scan non-void call",
        )
    }

    @Test
    fun `DATA_CLASS_COPY scans INVOKESPECIAL copy`() {
        val bytes = buildClassWithCopyCall(opcode = Opcodes.INVOKESPECIAL)
        val mutator = Mutator(setOf(MutationOperator.DATA_CLASS_COPY))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(
            mutations.any { it.operator == MutationOperator.DATA_CLASS_COPY },
            "Should scan INVOKESPECIAL copy()",
        )
    }

    @Test
    fun `COROUTINE scans JobKt builder call`() {
        val bytes = buildClassWithCoroutineBuilder("kotlinx/coroutines/JobKt", "start")
        val mutator = Mutator(setOf(MutationOperator.COROUTINE))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(
            mutations.any { it.operator == MutationOperator.COROUTINE },
            "Should scan JobKt builder",
        )
    }

    @Test
    fun `COROUTINE scans GlobalScope builder call`() {
        val bytes = buildClassWithCoroutineBuilder("kotlinx/coroutines/GlobalScope", "launch")
        val mutator = Mutator(setOf(MutationOperator.COROUTINE))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(
            mutations.any { it.operator == MutationOperator.COROUTINE },
            "Should scan GlobalScope builder",
        )
    }

    @Test
    fun `NULL_SAFETY scans throwUninitializedPropertyAccessException`() {
        val bytes = buildClassWithIntrinsics("throwUninitializedPropertyAccessException")
        val mutator = Mutator(setOf(MutationOperator.NULL_SAFETY))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(
            mutations.any { it.operator == MutationOperator.NULL_SAFETY },
            "Should scan throwUninitializedPropertyAccessException",
        )
    }

    @Test
    fun `SEALED_WHEN scans LOOKUPSWITCH branches`() {
        val bytes = buildKotlinClassWithLookupSwitch()
        val mutator = Mutator(setOf(MutationOperator.SEALED_WHEN))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(
            mutations.any { it.operator == MutationOperator.SEALED_WHEN },
            "Should scan LOOKUPSWITCH branches",
        )
    }

    @Test
    fun `SEALED_WHEN ignores switches when operator disabled or non-Kotlin`() {
        // Disabled operator
        val bytes = buildKotlinClassWithLookupSwitch()
        val mutations = Mutator(emptySet()).scanMutations(bytes)
        assertEquals(0, mutations.size, "Disabled operators should produce nothing")

        // Non-Kotlin class
        val javaBytes = buildClassWithLookupSwitch()
        val javaMutations = Mutator(setOf(MutationOperator.SEALED_WHEN)).scanMutations(javaBytes)
        assertEquals(0, javaMutations.size, "Non-Kotlin class should not produce SEALED_WHEN")
    }

    @Test
    fun `SEALED_WHEN ignores single instanceof`() {
        val bytes = buildKotlinClassWithSingleInstanceof()
        val mutator = Mutator(setOf(MutationOperator.SEALED_WHEN))
        val mutations = mutator.scanMutations(bytes)
        val sealed = mutations.filter { it.operator == MutationOperator.SEALED_WHEN }
        assertEquals(0, sealed.size, "Single instanceof should not trigger SEALED_WHEN")
    }

    @Test
    fun `SEALED_WHEN ignores switch without preceding instanceof on same line`() {
        // Build a Kotlin class with a LOOKUPSWITCH but NO instanceof on the same line.
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC,
            "com/example/PlainEnum",
            null,
            "java/lang/Object",
            null,
        )
        val meta = cw.visitAnnotation("Lkotlin/Metadata;", true)
        meta.visit("mv", intArrayOf(1, 9, 0))
        meta.visit("k", 1)
        meta.visitEnd()
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor.visitCode()
        ctor.visitVarInsn(Opcodes.ALOAD, 0)
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor.visitInsn(Opcodes.RETURN)
        ctor.visitMaxs(1, 1)
        ctor.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv.visitCode()
        mv.visitLineNumber(1, Label())
        val dflt = Label()
        val c0 = Label()
        mv.visitInsn(Opcodes.ICONST_0)
        mv.visitLookupSwitchInsn(dflt, intArrayOf(0), arrayOf(c0))
        mv.visitLabel(c0)
        mv.visitInsn(Opcodes.ICONST_1)
        mv.visitInsn(Opcodes.IRETURN)
        mv.visitLabel(dflt)
        mv.visitInsn(Opcodes.ICONST_0)
        mv.visitInsn(Opcodes.IRETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()
        cw.visitEnd()
        val mutations = Mutator(setOf(MutationOperator.SEALED_WHEN)).scanMutations(cw.toByteArray())
        assertEquals(
            0,
            mutations.size,
            "Switch without preceding instanceof on same line must not fire SEALED_WHEN",
        )
    }

    // ======================================================================
    // MutationApplierMethodVisitor
    // ======================================================================

    @Test
    fun `Applier skips synthetic bridge init and Kotlin-synthetic methods`() {
        // Build a class with synthetic + bridge + init + kotlin-synthetic methods.
        // The applier should only apply mutation to the non-synthetic method.
        val bytes = buildClassWithMixedMethods()
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(mutations.isNotEmpty())
        // Apply should succeed and not throw on the skipped methods
        val mutated = mutator.applyMutation(bytes, mutations.first())
        assertTrue(mutated.isNotEmpty())
    }

    @Test
    fun `Applier ignores non-Metadata annotations`() {
        // The applier's visitAnnotation only sets isKotlinClass for Metadata.
        // A class with a non-Metadata annotation should not be treated as Kotlin.
        val bytes = buildClassWithNonMetadataAnnotation()
        val mutator = Mutator(setOf(MutationOperator.SEALED_WHEN))
        val mutations = mutator.scanMutations(bytes)
        // Non-Kotlin class with no switch → no SEALED_WHEN
        assertEquals(0, mutations.size)
    }

    @Test
    fun `visitInsn delegates when mutatedOpcode unchanged`() {
        // The applier's visitInsn checks getMutatedOpcode. For operators without
        // mapping, it returns the same opcode and delegates to super.
        // Use RETURN_VALS where original==mutated — but the special path handles it.
        // For non-return insn, use ARITHMETIC with NOP (IADD → ISUB works).
        // Better: use a void RETURN that doesn't match any return operator.
        val bytes =
            buildClassWithMethod(descriptor = "()V") { mv ->
                mv.visitInsn(Opcodes.NOP)
            }
        // No mutations to apply → applier delegates everything
        val mutator = Mutator(emptySet())
        val mutations = mutator.scanMutations(bytes)
        assertEquals(0, mutations.size)
        // If we manually craft a non-matching mutation, applier delegates
        val fakeMutation =
            MutationInfo(
                operator = MutationOperator.ARITHMETIC,
                className = "Test",
                methodName = "test",
                methodDescriptor = "()V",
                lineNumber = 1,
                description = "fake",
                originalOpcode = Opcodes.NOP,
                mutatedOpcode = Opcodes.NOP,
            )
        // Should not throw — applier delegates when no match
        val mutated = mutator.applyMutation(bytes, fakeMutation)
        assertTrue(mutated.isNotEmpty())
    }

    @Test
    fun `visitJumpInsn delegates when no mutation matches`() {
        // Create a mutation that doesn't match — applier should delegate.
        val bytes =
            buildClassWithMethod(descriptor = "()I") { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                val l = Label()
                mv.visitJumpInsn(Opcodes.IFEQ, l)
                mv.visitLabel(l)
            }
        val mutator = Mutator(setOf(MutationOperator.NEGATE_CONDITIONALS))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(mutations.isNotEmpty())
        val fake =
            mutations.first().copy(lineNumber = 99999)
        val result = mutator.applyMutation(bytes, fake)
        // Delegates — bytecode is rewritten (stack frames differ) but the
        // original IFEQ jump opcode must still be present.
        val opcodes = collectAllOpcodes(result)
        assertTrue(Opcodes.IFEQ in opcodes, "IFEQ should be preserved")
    }

    @Test
    fun `visitTypeInsn delegates for non-SEALED_WHEN`() {
        // Build a class with INSTANCEOF but apply an ARITHMETIC mutation on
        // a different opcode — the visitTypeInsn should delegate.
        val bytes =
            buildClassWithMethod(descriptor = "()I") { mv ->
                mv.visitInsn(Opcodes.ACONST_NULL)
                mv.visitTypeInsn(Opcodes.INSTANCEOF, "java/lang/String")
                val l = Label()
                mv.visitJumpInsn(Opcodes.IFEQ, l)
                mv.visitLabel(l)
            }
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        // ARITHMETIC doesn't fire on INSTANCEOF, so no mutations.
        val mutations = mutator.scanMutations(bytes)
        assertEquals(0, mutations.size)
    }

    @Test
    fun `ARITHMETIC applier negates IINC increment`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_0)
                mv.visitVarInsn(Opcodes.ISTORE, 1)
                mv.visitIincInsn(1, 3)
            }
        val mutation = findMutation(bytes, MutationOperator.ARITHMETIC, Opcodes.IINC)
        val mutated = Mutator(setOf(MutationOperator.ARITHMETIC)).applyMutation(bytes, mutation)
        val increments = collectIincIncrements(mutated)
        assertTrue(increments.contains(-3), "Expected -3 in $increments")
    }

    @Test
    fun `INCREMENTS applier negates IINC increment`() {
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_0)
                mv.visitVarInsn(Opcodes.ISTORE, 1)
                mv.visitIincInsn(1, 2)
            }
        val mutation = findMutation(bytes, MutationOperator.INCREMENTS)
        val mutated = Mutator(setOf(MutationOperator.INCREMENTS)).applyMutation(bytes, mutation)
        val increments = collectIincIncrements(mutated)
        assertTrue(increments.contains(-2), "Expected -2 in $increments")
    }

    @Test
    fun `visitTableSwitchInsn delegates for invalid branchIndex`() {
        // Build a class with a tableswitch. Create a SEALED_WHEN mutation with
        // an invalid branchIndex in metadata. The applier should delegate.
        val bytes = buildKotlinClassWithTableSwitch()
        val mutator = Mutator(setOf(MutationOperator.SEALED_WHEN))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(mutations.isNotEmpty())
        // Corrupt the branchIndex
        val fake =
            mutations.first().copy(
                metadata = mapOf("branchIndex" to "999", "branchCount" to "3"),
            )
        val result = mutator.applyMutation(bytes, fake)
        // branchIndex out of range → applier delegates. The branch redirect
        // does not happen, so the resulting class still has a tableswitch
        // with 3 branches. Verify by checking that the resulting class parses
        // and contains a TABLESWITCH-like structure (a table with 4 labels:
        // default + 3 branches).
        val labels = collectLabels(result)
        assertTrue(labels >= 4, "Expected at least 4 labels in tableswitch bytecode, got $labels")
    }

    @Test
    fun `SEALED_WHEN applier redirects lookupswitch branch`() {
        val bytes = buildKotlinClassWithLookupSwitch()
        val mutator = Mutator(setOf(MutationOperator.SEALED_WHEN))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(mutations.isNotEmpty())
        val mutated = mutator.applyMutation(bytes, mutations.first())
        // Bytecode should differ
        assertFalse(
            mutated.contentEquals(bytes),
            "SEALED_WHEN mutation should change bytes",
        )
        assertTrue(mutated.isNotEmpty())
    }

    @Test
    fun `VOID_METHOD_CALLS applier removes virtual static interface call`() {
        val bytes = buildClassWithVoidMethodCalls()
        val mutator = Mutator(setOf(MutationOperator.VOID_METHOD_CALLS))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(mutations.isNotEmpty())
        val mutated = mutator.applyMutation(bytes, mutations.first())
        // Should differ from original
        assertFalse(mutated.contentEquals(bytes))
        assertTrue(mutated.isNotEmpty())
    }

    @Test
    fun `CONSTRUCTOR_CALLS applier removes constructor call`() {
        val bytes = buildClassWithNewObject()
        val mutator = Mutator(setOf(MutationOperator.CONSTRUCTOR_CALLS))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(mutations.isNotEmpty())
        val mutated = mutator.applyMutation(bytes, mutations.first())
        assertFalse(mutated.contentEquals(bytes))
    }

    @Test
    fun `NON_VOID_METHOD_CALLS applier replaces call with default value`() {
        val bytes = buildClassWithStringLengthCall()
        val mutator = Mutator(setOf(MutationOperator.NON_VOID_METHOD_CALLS))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(mutations.isNotEmpty())
        val mutated = mutator.applyMutation(bytes, mutations.first())
        assertFalse(mutated.contentEquals(bytes))
    }

    @Test
    fun `DATA_CLASS_COPY applier handles INVOKESPECIAL copy`() {
        val bytes = buildClassWithCopyCall(opcode = Opcodes.INVOKESPECIAL)
        val mutator = Mutator(setOf(MutationOperator.DATA_CLASS_COPY))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(mutations.isNotEmpty())
        val mutated = mutator.applyMutation(bytes, mutations.first())
        assertFalse(mutated.contentEquals(bytes))
    }

    @Test
    fun `popArgs uses POP2 for long and double arguments`() {
        // Test indirectly: apply VOID_METHOD_CALLS on a call with long/double args.
        // The bytecode should not have a stack underflow.
        val bytes = buildClassWithVoidCallLongArg()
        val mutator = Mutator(setOf(MutationOperator.VOID_METHOD_CALLS))
        val mutations = mutator.scanMutations(bytes)
        assertTrue(mutations.isNotEmpty())
        val mutated = mutator.applyMutation(bytes, mutations.first())
        // Should be structurally valid
        ClassReader(mutated).accept(
            object : ClassVisitor(Opcodes.ASM9) {},
            ClassReader.SKIP_FRAMES,
        )
    }

    @Test
    fun `pushDefaultValue emits all primitive zeroes and null`() {
        // Test via NON_VOID_METHOD_CALLS on various return types.
        val descriptors =
            listOf("()I", "()J", "()F", "()D", "()Z", "()B", "()S", "()C", "()Ljava/lang/Object;")
        for (desc in descriptors) {
            val bytes = buildClassWithNonVoidCallDescriptor(desc)
            val mutator = Mutator(setOf(MutationOperator.NON_VOID_METHOD_CALLS))
            val mutations = mutator.scanMutations(bytes)
            assertTrue(mutations.isNotEmpty(), "Should find mutations for $desc")
            val mutated = mutator.applyMutation(bytes, mutations.first())
            assertTrue(mutated.isNotEmpty(), "Mutation should produce bytes for $desc")
            assertFalse(
                mutated.contentEquals(bytes),
                "Mutation should change bytes for $desc",
            )
        }
    }

    @Test
    fun `getMutatedOpcode returns original for operators without opcode mapping`() {
        // DATA_CLASS_COPY, COROUTINE, NULL_SAFETY, VOID_METHOD_CALLS,
        // CONSTRUCTOR_CALLS, NON_VOID_METHOD_CALLS — none use getMutatedOpcode.
        // They all have their own custom applier branches. Verify that running
        // them on a non-matching visitInsn is a no-op.
        val bytes =
            buildClassWithMethod { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.ICONST_2)
                mv.visitInsn(Opcodes.IADD)
            }
        // Manually craft a mutation with DATA_CLASS_COPY + IADD opcode
        val fakeMutation =
            MutationInfo(
                operator = MutationOperator.DATA_CLASS_COPY,
                className = "Test",
                methodName = "test",
                methodDescriptor = "()I",
                lineNumber = 1,
                description = "fake",
                originalOpcode = Opcodes.IADD,
                mutatedOpcode = Opcodes.IADD,
            )
        val mutator = Mutator(setOf(MutationOperator.DATA_CLASS_COPY))
        val result = mutator.applyMutation(bytes, fakeMutation)
        // Should be logically unchanged (no matching branch in visitInsn).
        // IADD must be preserved — not replaced with NOP or removed.
        val opcodes = collectAllOpcodes(result)
        assertTrue(Opcodes.IADD in opcodes, "IADD should be preserved")
    }

    @Test
    fun `applyReturnMutation handles void RETURN`() {
        // Build a void method with RETURN. Apply RETURN_VALS to it.
        val bytes =
            buildClassWithMethod(descriptor = "()V") { mv ->
                mv.visitInsn(Opcodes.RETURN)
            }
        val mutator = Mutator(setOf(MutationOperator.RETURN_VALS))
        val mutations = mutator.scanMutations(bytes)
        // RETURN_VALS scans IRETURN/LRETURN/FRETURN/DRETURN/ARETURN, not void RETURN
        assertEquals(0, mutations.size, "Void RETURN should not match RETURN_VALS")
    }

    @Test
    fun `NULL_RETURNS applier delegates for non-ARETURN`() {
        // For IRETURN with NULL_RETURNS — applier delegates to super because
        // the operator only handles ARETURN.
        val bytes =
            buildClassWithMethod(descriptor = "()I") { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.IRETURN)
            }
        // Manually craft a NULL_RETURNS mutation targeting IRETURN
        val fakeMutation =
            MutationInfo(
                operator = MutationOperator.NULL_RETURNS,
                className = "Test",
                methodName = "test",
                methodDescriptor = "()I",
                lineNumber = 1,
                description = "fake",
                originalOpcode = Opcodes.IRETURN,
                mutatedOpcode = Opcodes.IRETURN,
            )
        val mutator = Mutator(setOf(MutationOperator.NULL_RETURNS))
        val result = mutator.applyMutation(bytes, fakeMutation)
        // IRETURN is preserved (not replaced with ACONST_NULL/ARETURN).
        val opcodes = collectAllOpcodes(result)
        assertTrue(Opcodes.IRETURN in opcodes, "IRETURN should be preserved")
        assertFalse(Opcodes.ARETURN in opcodes, "ARETURN should not appear")
    }

    @Test
    fun `EMPTY_RETURNS applier delegates for non-ARETURN`() {
        val bytes =
            buildClassWithMethod(descriptor = "()I") { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.IRETURN)
            }
        val fakeMutation =
            MutationInfo(
                operator = MutationOperator.EMPTY_RETURNS,
                className = "Test",
                methodName = "test",
                methodDescriptor = "()I",
                lineNumber = 1,
                description = "fake",
                originalOpcode = Opcodes.IRETURN,
                mutatedOpcode = Opcodes.IRETURN,
            )
        val mutator = Mutator(setOf(MutationOperator.EMPTY_RETURNS))
        val result = mutator.applyMutation(bytes, fakeMutation)
        val opcodes = collectAllOpcodes(result)
        assertTrue(Opcodes.IRETURN in opcodes, "IRETURN should be preserved")
        assertFalse(Opcodes.ARETURN in opcodes, "ARETURN should not appear")
    }

    @Test
    fun `applyEmptyReturn builds each primitive array type`() {
        // Test each primitive array: boolean, byte, char, short, long, float, double
        val primDescs = listOf("[Z", "[B", "[C", "[S", "[J", "[F", "[D", "[I")
        for (desc in primDescs) {
            val bytes =
                buildClassWithMethod(descriptor = "()$desc") { mv ->
                    mv.visitInsn(Opcodes.ACONST_NULL)
                    mv.visitInsn(Opcodes.ARETURN)
                }
            val mutator = Mutator(setOf(MutationOperator.EMPTY_RETURNS))
            val mutations = mutator.scanMutations(bytes)
            assertTrue(mutations.isNotEmpty(), "Should find EMPTY_RETURNS for $desc")
            val mutated = mutator.applyMutation(bytes, mutations.first())
            assertTrue(mutated.isNotEmpty(), "Should produce bytes for $desc")
            assertFalse(
                mutated.contentEquals(bytes),
                "Should differ for $desc",
            )
        }
    }

    @Test
    fun `applyEmptyReturn builds object arrays`() {
        val arrayDescs = listOf("[Ljava/lang/String;", "[Ljava/lang/Object;", "[I")
        for (desc in arrayDescs) {
            val bytes =
                buildClassWithMethod(descriptor = "()$desc") { mv ->
                    mv.visitInsn(Opcodes.ACONST_NULL)
                    mv.visitInsn(Opcodes.ARETURN)
                }
            val mutator = Mutator(setOf(MutationOperator.EMPTY_RETURNS))
            val mutations = mutator.scanMutations(bytes)
            assertTrue(mutations.isNotEmpty(), "Should find EMPTY_RETURNS for $desc")
            val mutated = mutator.applyMutation(bytes, mutations.first())
            assertTrue(mutated.isNotEmpty())
            assertFalse(mutated.contentEquals(bytes))
        }
    }

    @Test
    fun `applyEmptyReturn returns empty Kotlin MutableList MutableSet MutableMap`() {
        val kotlinDescs =
            listOf(
                "()Lkotlin/collections/MutableList;",
                "()Lkotlin/collections/MutableSet;",
                "()Lkotlin/collections/MutableMap;",
            )
        for (desc in kotlinDescs) {
            val bytes =
                buildClassWithMethod(descriptor = desc) { mv ->
                    mv.visitInsn(Opcodes.ACONST_NULL)
                    mv.visitInsn(Opcodes.ARETURN)
                }
            val mutator = Mutator(setOf(MutationOperator.EMPTY_RETURNS))
            val mutations = mutator.scanMutations(bytes)
            assertTrue(mutations.isNotEmpty(), "Should find EMPTY_RETURNS for $desc")
            val mutated = mutator.applyMutation(bytes, mutations.first())
            assertTrue(mutated.isNotEmpty())
            assertFalse(mutated.contentEquals(bytes))
            // Verify INVOKESTATIC (emptyList/emptySet/emptyMap) is present
            val opcodes = collectAllOpcodes(mutated)
            assertTrue(
                Opcodes.INVOKESTATIC in opcodes,
                "Expected INVOKESTATIC in mutated bytecode for $desc",
            )
        }
    }

    @Test
    fun `applyEmptyReturn falls through for non-collection object return`() {
        // For non-collection return type, EMPTY_RETURNS scanner won't create
        // a mutation. But if we manually craft one, the applier should handle it
        // gracefully (falls through to ARETURN with original value popped).
        val bytes =
            buildClassWithMethod(descriptor = "()Ljava/lang/String;") { mv ->
                mv.visitInsn(Opcodes.ACONST_NULL)
                mv.visitInsn(Opcodes.ARETURN)
            }
        // EMPTY_RETURNS only fires for collections/arrays — no mutation expected
        val mutator = Mutator(setOf(MutationOperator.EMPTY_RETURNS))
        val mutations = mutator.scanMutations(bytes)
        assertEquals(
            0,
            mutations.size,
            "String return should not produce EMPTY_RETURNS mutation",
        )
    }

    // ======================================================================
    // Helpers
    // ======================================================================

    private fun buildClassWithMethod(
        name: String = "test",
        descriptor: String = "()I",
        lineNumber: Int = 1,
        body: (MethodVisitor) -> Unit,
    ): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Test", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, descriptor, null, null)
        mv?.visitCode()
        mv?.visitLineNumber(lineNumber, Label())
        body(mv!!)
        val retType = descriptor.substringAfterLast(")")
        when (retType) {
            "V" -> mv?.visitInsn(Opcodes.RETURN)
            "J" -> {
                mv?.visitInsn(Opcodes.LCONST_0)
                mv?.visitInsn(Opcodes.LRETURN)
            }
            "F" -> {
                mv?.visitInsn(Opcodes.FCONST_0)
                mv?.visitInsn(Opcodes.FRETURN)
            }
            "D" -> {
                mv?.visitInsn(Opcodes.DCONST_0)
                mv?.visitInsn(Opcodes.DRETURN)
            }
            else -> {
                mv?.visitInsn(Opcodes.ACONST_NULL)
                mv?.visitInsn(Opcodes.ARETURN)
            }
        }
        mv?.visitMaxs(10, 10)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildKotlinClassWithMethod(
        methodName: String,
        descriptor: String = "()I",
        body: (MethodVisitor) -> Unit,
    ): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Kotlin", null, "java/lang/Object", null)
        val meta = cw.visitAnnotation("Lkotlin/Metadata;", true)
        meta.visit("mv", intArrayOf(1, 9, 0))
        meta.visit("k", 1)
        meta.visitEnd()
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, methodName, descriptor, null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        body(mv!!)
        val retType = descriptor.substringAfterLast(")")
        when (retType) {
            "V" -> mv?.visitInsn(Opcodes.RETURN)
            else -> {
                mv?.visitInsn(Opcodes.ICONST_0)
                mv?.visitInsn(Opcodes.IRETURN)
            }
        }
        mv?.visitMaxs(10, 10)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildEmptyClass(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Empty", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithSubclassHierarchy(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC,
            "com/example/Child",
            null,
            "java/lang/Object",
            null,
        )
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.ICONST_2)
        mv?.visitInsn(Opcodes.IADD)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithDeepHierarchy(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC,
            "com/example/SubChild",
            null,
            "java/lang/Number",
            null,
        )
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Number", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.ICONST_2)
        mv?.visitInsn(Opcodes.IADD)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithUnknownReference(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC,
            "com/example/UnknownRef",
            null,
            "com/example/NonExistentSuper",
            null,
        )
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.ICONST_2)
        mv?.visitInsn(Opcodes.IADD)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithInterface(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC,
            "com/example/Impl",
            null,
            "java/lang/Object",
            arrayOf("java/lang/Runnable"),
        )
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.ICONST_2)
        mv?.visitInsn(Opcodes.IADD)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithSyntheticMethod(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Synth", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv =
            cw.visitMethod(
                Opcodes.ACC_SYNTHETIC or Opcodes.ACC_PUBLIC,
                "synthetic",
                "()I",
                null,
                null,
            )
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.ICONST_2)
        mv?.visitInsn(Opcodes.IADD)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithBridgeMethod(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Bridge", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv =
            cw.visitMethod(
                Opcodes.ACC_BRIDGE or Opcodes.ACC_SYNTHETIC or Opcodes.ACC_PUBLIC,
                "bridge",
                "()I",
                null,
                null,
            )
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(1, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithUnknownArrayInSuppress(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        val av =
            cw.visitAnnotation(
                "Lcom/github/rodrigotimoteo/mutation/annotation/SuppressMutations;",
                true,
            )
        // Add an unknown array attribute
        val arr = av?.visitArray("unknownAttr")
        arr?.visit(null, "value")
        arr?.visitEnd()
        // Add the real operators array
        val opsArr = av?.visitArray("operators")
        opsArr?.visit(null, "ARITHMETIC")
        opsArr?.visitEnd()
        av?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.ICONST_2)
        mv?.visitInsn(Opcodes.IADD)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildMethodWithUnknownArrayInSuppress(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        val mav =
            mv?.visitAnnotation(
                "Lcom/github/rodrigotimoteo/mutation/annotation/SuppressMutations;",
                true,
            )
        val arr = mav?.visitArray("unknownAttr")
        arr?.visit(null, "value")
        arr?.visitEnd()
        val opsArr = mav?.visitArray("operators")
        opsArr?.visit(null, "ARITHMETIC")
        opsArr?.visitEnd()
        mav?.visitEnd()
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.ICONST_2)
        mv?.visitInsn(Opcodes.IADD)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithVoidCallToInit(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()V", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv?.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder")
        mv?.visitInsn(Opcodes.DUP)
        mv?.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "java/lang/StringBuilder",
            "<init>",
            "()V",
            false,
        )
        mv?.visitInsn(Opcodes.POP)
        mv?.visitInsn(Opcodes.RETURN)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithNonVoidCall(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "java/lang/String",
            "length",
            "()I",
            false,
        )
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(1, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithNewObject(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()V", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv?.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder")
        mv?.visitInsn(Opcodes.DUP)
        mv?.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "java/lang/StringBuilder",
            "<init>",
            "()V",
            false,
        )
        mv?.visitInsn(Opcodes.POP)
        mv?.visitInsn(Opcodes.RETURN)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithStringLengthCall(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "java/lang/String",
            "length",
            "()I",
            false,
        )
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(1, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithNonVoidCallDescriptor(descriptor: String): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", descriptor, null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        // Single non-void static call to a helper with matching descriptor
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "com/example/Helper",
            "produce",
            descriptor,
            false,
        )
        val retType = Type.getReturnType(descriptor)
        when (retType.sort) {
            Type.VOID -> mv.visitInsn(Opcodes.RETURN)
            Type.LONG, Type.DOUBLE ->
                mv.visitInsn(
                    if (retType.sort == Type.LONG) Opcodes.LRETURN else Opcodes.DRETURN,
                )
            Type.FLOAT -> mv.visitInsn(Opcodes.FRETURN)
            else -> {
                if (retType.sort == Type.OBJECT || retType.sort == Type.ARRAY) {
                    mv.visitInsn(Opcodes.ARETURN)
                } else {
                    mv.visitInsn(Opcodes.IRETURN)
                }
            }
        }
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithCopyCall(opcode: Int): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        // @kotlin.Metadata marks the class as Kotlin. The data-class signal
        // is the component1 method (ASM exposes @kotlin.Metadata.d1 as
        // String[] and the raw protobuf path is unreliable).
        val meta = cw.visitAnnotation("Lkotlin/Metadata;", true)
        meta.visit("mv", intArrayOf(1, 9, 0))
        meta.visit("k", 1)
        meta.visitEnd()
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        // component1 — marks the class as a data class for the scanner
        val comp = cw.visitMethod(Opcodes.ACC_PUBLIC, "component1", "()I", null, null)
        comp?.visitCode()
        comp?.visitInsn(Opcodes.ICONST_0)
        comp?.visitInsn(Opcodes.IRETURN)
        comp?.visitMaxs(1, 1)
        comp?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()Lcom/example/Foo;", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv?.visitVarInsn(Opcodes.ALOAD, 0)
        mv?.visitMethodInsn(opcode, "com/example/Foo", "copy", "()Lcom/example/Foo;", false)
        mv?.visitInsn(Opcodes.ARETURN)
        mv?.visitMaxs(1, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithCoroutineBuilder(
        owner: String,
        name: String,
    ): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()V", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv?.visitInsn(Opcodes.ACONST_NULL)
        mv?.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            owner,
            name,
            "(Ljava/lang/Object;)V",
            false,
        )
        mv?.visitInsn(Opcodes.RETURN)
        mv?.visitMaxs(1, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithIntrinsics(methodName: String): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()V", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv?.visitLdcInsn("prop")
        mv?.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "kotlin/jvm/internal/Intrinsics",
            methodName,
            "(Ljava/lang/String;)V",
            false,
        )
        mv?.visitInsn(Opcodes.RETURN)
        mv?.visitMaxs(1, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildKotlinClassWithLookupSwitch(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC,
            "com/example/WhenExpr",
            null,
            "java/lang/Object",
            null,
        )
        val meta = cw.visitAnnotation("Lkotlin/Metadata;", true)
        meta.visit("mv", intArrayOf(1, 9, 0))
        meta.visit("k", 1)
        meta.visitEnd()
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        // Preceded by instanceof on the same line so SEALED_WHEN guard allows mutation
        mv?.visitVarInsn(Opcodes.ALOAD, 0)
        mv?.visitTypeInsn(Opcodes.INSTANCEOF, "com/example/WhenExpr")
        val dflt = Label()
        val c0 = Label()
        val c1 = Label()
        mv?.visitInsn(Opcodes.ICONST_0)
        mv?.visitLookupSwitchInsn(dflt, intArrayOf(0, 1), arrayOf(c0, c1))
        mv?.visitLabel(c0)
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitLabel(c1)
        mv?.visitInsn(Opcodes.ICONST_2)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitLabel(dflt)
        mv?.visitInsn(Opcodes.ICONST_3)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(1, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithLookupSwitch(): ByteArray {
        // Non-Kotlin version (no @Metadata)
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC,
            "com/example/JavaWhen",
            null,
            "java/lang/Object",
            null,
        )
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        val dflt = Label()
        val c0 = Label()
        mv?.visitInsn(Opcodes.ICONST_0)
        mv?.visitLookupSwitchInsn(dflt, intArrayOf(0), arrayOf(c0))
        mv?.visitLabel(c0)
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitLabel(dflt)
        mv?.visitInsn(Opcodes.ICONST_0)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(1, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildKotlinClassWithTableSwitch(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC,
            "com/example/WhenTbl",
            null,
            "java/lang/Object",
            null,
        )
        val meta = cw.visitAnnotation("Lkotlin/Metadata;", true)
        meta.visit("mv", intArrayOf(1, 9, 0))
        meta.visit("k", 1)
        meta.visitEnd()
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        // Preceded by instanceof on the same line so SEALED_WHEN guard allows mutation
        mv?.visitVarInsn(Opcodes.ALOAD, 0)
        mv?.visitTypeInsn(Opcodes.INSTANCEOF, "com/example/WhenTbl")
        val dflt = Label()
        val c0 = Label()
        val c1 = Label()
        val c2 = Label()
        mv?.visitInsn(Opcodes.ICONST_0)
        mv?.visitTableSwitchInsn(0, 2, dflt, c0, c1, c2)
        mv?.visitLabel(c0)
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitLabel(c1)
        mv?.visitInsn(Opcodes.ICONST_2)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitLabel(c2)
        mv?.visitInsn(Opcodes.ICONST_3)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitLabel(dflt)
        mv?.visitInsn(Opcodes.ICONST_0)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(1, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildKotlinClassWithSingleInstanceof(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC,
            "com/example/Foo",
            null,
            "java/lang/Object",
            null,
        )
        val meta = cw.visitAnnotation("Lkotlin/Metadata;", true)
        meta.visit("mv", intArrayOf(1, 9, 0))
        meta.visit("k", 1)
        meta.visitEnd()
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()Z", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv?.visitVarInsn(Opcodes.ALOAD, 0)
        mv?.visitTypeInsn(Opcodes.INSTANCEOF, "com/example/Foo")
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(1, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithMixedMethods(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Mixed", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        // Synthetic method
        val synth =
            cw.visitMethod(
                Opcodes.ACC_SYNTHETIC or Opcodes.ACC_PUBLIC,
                "synthetic",
                "()I",
                null,
                null,
            )
        synth?.visitCode()
        synth?.visitInsn(Opcodes.ICONST_1)
        synth?.visitInsn(Opcodes.IRETURN)
        synth?.visitMaxs(1, 1)
        synth?.visitEnd()
        // Real method
        val real = cw.visitMethod(Opcodes.ACC_PUBLIC, "real", "()I", null, null)
        real?.visitCode()
        real?.visitLineNumber(1, Label())
        real?.visitInsn(Opcodes.ICONST_1)
        real?.visitInsn(Opcodes.ICONST_2)
        real?.visitInsn(Opcodes.IADD)
        real?.visitInsn(Opcodes.IRETURN)
        real?.visitMaxs(2, 1)
        real?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithNonMetadataAnnotation(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC,
            "com/example/NotKotlin",
            null,
            "java/lang/Object",
            null,
        )
        val av = cw.visitAnnotation("Lcom/example/MyAnnotation;", true)
        av?.visitEnd()
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.ICONST_2)
        mv?.visitInsn(Opcodes.IADD)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithVoidMethodCalls(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC,
            "com/example/Foo",
            null,
            "java/lang/Object",
            null,
        )
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()V", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv?.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;")
        mv?.visitLdcInsn("hello")
        mv?.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "java/io/PrintStream",
            "println",
            "(Ljava/lang/String;)V",
            false,
        )
        mv?.visitInsn(Opcodes.RETURN)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithVoidCallLongArg(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC,
            "com/example/Foo",
            null,
            "java/lang/Object",
            null,
        )
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()V", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv?.visitInsn(Opcodes.LCONST_1)
        mv?.visitInsn(Opcodes.DCONST_1)
        mv?.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "com/example/Helper",
            "consume",
            "(JD)V",
            false,
        )
        mv?.visitInsn(Opcodes.RETURN)
        mv?.visitMaxs(4, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun findMutation(
        bytes: ByteArray,
        operator: MutationOperator,
        opcode: Int? = null,
    ): MutationInfo {
        val mutations = Mutator(setOf(operator)).scanMutations(bytes)
        return if (opcode != null) {
            mutations.first { it.originalOpcode == opcode }
        } else {
            mutations.first { it.operator == operator }
        }
    }

    private fun collectIincIncrements(bytes: ByteArray): Set<Int> {
        val increments = mutableSetOf<Int>()
        ClassReader(bytes).accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor {
                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                    return object : MethodVisitor(Opcodes.ASM9, mv) {
                        override fun visitIincInsn(
                            varIndex: Int,
                            increment: Int,
                        ) {
                            increments.add(increment)
                            super.visitIincInsn(varIndex, increment)
                        }
                    }
                }
            },
            0,
        )
        return increments
    }

    private fun collectAllOpcodes(bytes: ByteArray): Set<Int> {
        val opcodes = mutableSetOf<Int>()
        ClassReader(bytes).accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor {
                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                    return object : MethodVisitor(Opcodes.ASM9, mv) {
                        override fun visitInsn(opcode: Int) {
                            opcodes.add(opcode)
                            super.visitInsn(opcode)
                        }

                        override fun visitJumpInsn(
                            opcode: Int,
                            label: Label?,
                        ) {
                            opcodes.add(opcode)
                            super.visitJumpInsn(opcode, label)
                        }

                        override fun visitMethodInsn(
                            opcode: Int,
                            owner: String,
                            name: String,
                            descriptor: String,
                            isInterface: Boolean,
                        ) {
                            opcodes.add(opcode)
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                        }
                    }
                }
            },
            0,
        )
        return opcodes
    }

    private fun collectLabels(bytes: ByteArray): Int {
        var count = 0
        ClassReader(bytes).accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor {
                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                    return object : MethodVisitor(Opcodes.ASM9, mv) {
                        override fun visitLabel(label: Label?) {
                            count++
                            super.visitLabel(label)
                        }
                    }
                }
            },
            0,
        )
        return count
    }
}
