package com.github.rodrigotimoteo.mutation.mutator

import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedList
import java.util.TreeMap
import java.util.TreeSet
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConditionalMutatorTest {
    @Test
    fun `mutateBoundaryStatic IFLT becomes IFLE`() {
        assertEquals(Opcodes.IFLE, ConditionalMutator.mutateBoundaryStatic(Opcodes.IFLT))
    }

    @Test
    fun `mutateBoundaryStatic IFLE becomes IFLT`() {
        assertEquals(Opcodes.IFLT, ConditionalMutator.mutateBoundaryStatic(Opcodes.IFLE))
    }

    @Test
    fun `mutateBoundaryStatic IFGE becomes IFGT`() {
        assertEquals(Opcodes.IFGT, ConditionalMutator.mutateBoundaryStatic(Opcodes.IFGE))
    }

    @Test
    fun `mutateBoundaryStatic IFGT becomes IFGE`() {
        assertEquals(Opcodes.IFGE, ConditionalMutator.mutateBoundaryStatic(Opcodes.IFGT))
    }

    @Test
    fun `mutateBoundaryStatic IF_ICMPLT becomes IF_ICMPLE`() {
        assertEquals(Opcodes.IF_ICMPLE, ConditionalMutator.mutateBoundaryStatic(Opcodes.IF_ICMPLT))
    }

    @Test
    fun `mutateBoundaryStatic IF_ICMPLE becomes IF_ICMPLT`() {
        assertEquals(Opcodes.IF_ICMPLT, ConditionalMutator.mutateBoundaryStatic(Opcodes.IF_ICMPLE))
    }

    @Test
    fun `mutateBoundaryStatic IF_ICMPGT becomes IF_ICMPGE`() {
        assertEquals(Opcodes.IF_ICMPGE, ConditionalMutator.mutateBoundaryStatic(Opcodes.IF_ICMPGT))
    }

    @Test
    fun `mutateBoundaryStatic IF_ICMPGE becomes IF_ICMPGT`() {
        assertEquals(Opcodes.IF_ICMPGT, ConditionalMutator.mutateBoundaryStatic(Opcodes.IF_ICMPGE))
    }

    @Test
    fun `mutateBoundaryStatic unknown opcode returns same`() {
        assertEquals(Opcodes.IFEQ, ConditionalMutator.mutateBoundaryStatic(Opcodes.IFEQ))
    }

    @Test
    fun `mutateBoundaryStatic IF_ACMPEQ is no-op (not a boundary)`() {
        // IF_ACMPEQ is reference equality, not a boundary
        assertEquals(Opcodes.IF_ACMPEQ, ConditionalMutator.mutateBoundaryStatic(Opcodes.IF_ACMPEQ))
    }

    @Test
    fun `mutateBoundaryStatic IF_ACMPNE is no-op`() {
        assertEquals(Opcodes.IF_ACMPNE, ConditionalMutator.mutateBoundaryStatic(Opcodes.IF_ACMPNE))
    }

    @Test
    fun `mutateBoundaryStatic IFNULL is no-op`() {
        assertEquals(Opcodes.IFNULL, ConditionalMutator.mutateBoundaryStatic(Opcodes.IFNULL))
    }

    @Test
    fun `mutateNegateStatic IFEQ becomes IFNE`() {
        assertEquals(Opcodes.IFNE, ConditionalMutator.mutateNegateStatic(Opcodes.IFEQ))
    }

    @Test
    fun `mutateNegateStatic IFNE becomes IFEQ`() {
        assertEquals(Opcodes.IFEQ, ConditionalMutator.mutateNegateStatic(Opcodes.IFNE))
    }

    @Test
    fun `mutateNegateStatic IFLT becomes IFGE`() {
        assertEquals(Opcodes.IFGE, ConditionalMutator.mutateNegateStatic(Opcodes.IFLT))
    }

    @Test
    fun `mutateNegateStatic IFGE becomes IFLT`() {
        assertEquals(Opcodes.IFLT, ConditionalMutator.mutateNegateStatic(Opcodes.IFGE))
    }

    @Test
    fun `mutateNegateStatic IFGT becomes IFLE`() {
        assertEquals(Opcodes.IFLE, ConditionalMutator.mutateNegateStatic(Opcodes.IFGT))
    }

    @Test
    fun `mutateNegateStatic IFLE becomes IFGT`() {
        assertEquals(Opcodes.IFGT, ConditionalMutator.mutateNegateStatic(Opcodes.IFLE))
    }

    @Test
    fun `mutateNegateStatic IF_ICMPEQ becomes IF_ICMPNE`() {
        assertEquals(Opcodes.IF_ICMPNE, ConditionalMutator.mutateNegateStatic(Opcodes.IF_ICMPEQ))
    }

    @Test
    fun `mutateNegateStatic IF_ICMPNE becomes IF_ICMPEQ`() {
        assertEquals(Opcodes.IF_ICMPEQ, ConditionalMutator.mutateNegateStatic(Opcodes.IF_ICMPNE))
    }

    @Test
    fun `mutateNegateStatic IF_ICMPLT becomes IF_ICMPGE`() {
        assertEquals(Opcodes.IF_ICMPGE, ConditionalMutator.mutateNegateStatic(Opcodes.IF_ICMPLT))
    }

    @Test
    fun `mutateNegateStatic IF_ICMPGE becomes IF_ICMPLT`() {
        assertEquals(Opcodes.IF_ICMPLT, ConditionalMutator.mutateNegateStatic(Opcodes.IF_ICMPGE))
    }

    @Test
    fun `mutateNegateStatic IF_ICMPGT becomes IF_ICMPLE`() {
        assertEquals(Opcodes.IF_ICMPLE, ConditionalMutator.mutateNegateStatic(Opcodes.IF_ICMPGT))
    }

    @Test
    fun `mutateNegateStatic IF_ICMPLE becomes IF_ICMPGT`() {
        assertEquals(Opcodes.IF_ICMPGT, ConditionalMutator.mutateNegateStatic(Opcodes.IF_ICMPLE))
    }

    @Test
    fun `mutateNegateStatic IF_ACMPEQ becomes IF_ACMPNE`() {
        assertEquals(Opcodes.IF_ACMPNE, ConditionalMutator.mutateNegateStatic(Opcodes.IF_ACMPEQ))
    }

    @Test
    fun `mutateNegateStatic IF_ACMPNE becomes IF_ACMPEQ`() {
        assertEquals(Opcodes.IF_ACMPEQ, ConditionalMutator.mutateNegateStatic(Opcodes.IF_ACMPNE))
    }

    @Test
    fun `mutateNegateStatic IFNULL becomes IFNONNULL`() {
        assertEquals(Opcodes.IFNONNULL, ConditionalMutator.mutateNegateStatic(Opcodes.IFNULL))
    }

    @Test
    fun `mutateNegateStatic IFNONNULL becomes IFNULL`() {
        assertEquals(Opcodes.IFNULL, ConditionalMutator.mutateNegateStatic(Opcodes.IFNONNULL))
    }

    @Test
    fun `mutateNegateStatic unknown opcode returns same`() {
        assertEquals(Opcodes.ICONST_0, ConditionalMutator.mutateNegateStatic(Opcodes.ICONST_0))
    }
}

class ArithmeticMutatorTest {
    @Test
    fun `mutateStatic IADD becomes ISUB`() {
        assertEquals(Opcodes.ISUB, ArithmeticMutator.mutateStatic(Opcodes.IADD))
    }

    @Test
    fun `mutateStatic ISUB becomes IADD`() {
        assertEquals(Opcodes.IADD, ArithmeticMutator.mutateStatic(Opcodes.ISUB))
    }

    @Test
    fun `mutateStatic IMUL becomes IDIV`() {
        assertEquals(Opcodes.IDIV, ArithmeticMutator.mutateStatic(Opcodes.IMUL))
    }

    @Test
    fun `mutateStatic IDIV becomes IMUL`() {
        assertEquals(Opcodes.IMUL, ArithmeticMutator.mutateStatic(Opcodes.IDIV))
    }

    @Test
    fun `mutateStatic IREM becomes IMUL`() {
        assertEquals(Opcodes.IMUL, ArithmeticMutator.mutateStatic(Opcodes.IREM))
    }

    @Test
    fun `mutateStatic LADD becomes LSUB`() {
        assertEquals(Opcodes.LSUB, ArithmeticMutator.mutateStatic(Opcodes.LADD))
    }

    @Test
    fun `mutateStatic LSUB becomes LADD`() {
        assertEquals(Opcodes.LADD, ArithmeticMutator.mutateStatic(Opcodes.LSUB))
    }

    @Test
    fun `mutateStatic LMUL becomes LDIV`() {
        assertEquals(Opcodes.LDIV, ArithmeticMutator.mutateStatic(Opcodes.LMUL))
    }

    @Test
    fun `mutateStatic LDIV becomes LMUL`() {
        assertEquals(Opcodes.LMUL, ArithmeticMutator.mutateStatic(Opcodes.LDIV))
    }

    @Test
    fun `mutateStatic LREM becomes LMUL`() {
        assertEquals(Opcodes.LMUL, ArithmeticMutator.mutateStatic(Opcodes.LREM))
    }

    @Test
    fun `mutateStatic FADD becomes FSUB`() {
        assertEquals(Opcodes.FSUB, ArithmeticMutator.mutateStatic(Opcodes.FADD))
    }

    @Test
    fun `mutateStatic FSUB becomes FADD`() {
        assertEquals(Opcodes.FADD, ArithmeticMutator.mutateStatic(Opcodes.FSUB))
    }

    @Test
    fun `mutateStatic FMUL becomes FDIV`() {
        assertEquals(Opcodes.FDIV, ArithmeticMutator.mutateStatic(Opcodes.FMUL))
    }

    @Test
    fun `mutateStatic FDIV becomes FMUL`() {
        assertEquals(Opcodes.FMUL, ArithmeticMutator.mutateStatic(Opcodes.FDIV))
    }

    @Test
    fun `mutateStatic FREM becomes FMUL`() {
        assertEquals(Opcodes.FMUL, ArithmeticMutator.mutateStatic(Opcodes.FREM))
    }

    @Test
    fun `mutateStatic DADD becomes DSUB`() {
        assertEquals(Opcodes.DSUB, ArithmeticMutator.mutateStatic(Opcodes.DADD))
    }

    @Test
    fun `mutateStatic DSUB becomes DADD`() {
        assertEquals(Opcodes.DADD, ArithmeticMutator.mutateStatic(Opcodes.DSUB))
    }

    @Test
    fun `mutateStatic DMUL becomes DDIV`() {
        assertEquals(Opcodes.DDIV, ArithmeticMutator.mutateStatic(Opcodes.DMUL))
    }

    @Test
    fun `mutateStatic DDIV becomes DMUL`() {
        assertEquals(Opcodes.DMUL, ArithmeticMutator.mutateStatic(Opcodes.DDIV))
    }

    @Test
    fun `mutateStatic DREM becomes DMUL`() {
        assertEquals(Opcodes.DMUL, ArithmeticMutator.mutateStatic(Opcodes.DREM))
    }

    @Test
    fun `mutateStatic INEG is no-op (unsafe)`() {
        // INEG cannot be replaced with NOP because of stack effects
        assertEquals(Opcodes.INEG, ArithmeticMutator.mutateStatic(Opcodes.INEG))
    }

    @Test
    fun `mutateStatic LNEG is no-op`() {
        assertEquals(Opcodes.LNEG, ArithmeticMutator.mutateStatic(Opcodes.LNEG))
    }

    @Test
    fun `mutateStatic FNEG is no-op`() {
        assertEquals(Opcodes.FNEG, ArithmeticMutator.mutateStatic(Opcodes.FNEG))
    }

    @Test
    fun `mutateStatic DNEG is no-op`() {
        assertEquals(Opcodes.DNEG, ArithmeticMutator.mutateStatic(Opcodes.DNEG))
    }

    @Test
    fun `mutateStatic unknown opcode returns same`() {
        assertEquals(Opcodes.ICONST_0, ArithmeticMutator.mutateStatic(Opcodes.ICONST_0))
    }
}

class ReturnValueMutatorTest {
    @Test
    fun `mutateReturnStatic IRETURN returns IRETURN (no mutation for return ops)`() {
        // ReturnValueMutator only mutates void returns. For typed returns,
        // it just returns the same opcode (other mutators handle them)
        assertEquals(Opcodes.IRETURN, ReturnValueMutator.mutateReturnStatic(Opcodes.IRETURN, Type.INT_TYPE))
    }

    @Test
    fun `mutateReturnStatic LRETURN returns LRETURN`() {
        assertEquals(Opcodes.LRETURN, ReturnValueMutator.mutateReturnStatic(Opcodes.LRETURN, Type.LONG_TYPE))
    }

    @Test
    fun `mutateReturnStatic ARETURN returns ARETURN`() {
        assertEquals(Opcodes.ARETURN, ReturnValueMutator.mutateReturnStatic(Opcodes.ARETURN, Type.getType(String::class.java)))
    }

    @Test
    fun `mutateReturnStatic FRETURN returns FRETURN`() {
        assertEquals(Opcodes.FRETURN, ReturnValueMutator.mutateReturnStatic(Opcodes.FRETURN, Type.FLOAT_TYPE))
    }

    @Test
    fun `mutateReturnStatic DRETURN returns DRETURN`() {
        assertEquals(Opcodes.DRETURN, ReturnValueMutator.mutateReturnStatic(Opcodes.DRETURN, Type.DOUBLE_TYPE))
    }

    @Test
    fun `mutateReturnStatic unknown opcode returns same`() {
        assertEquals(Opcodes.ICONST_0, ReturnValueMutator.mutateReturnStatic(Opcodes.ICONST_0, Type.INT_TYPE))
    }

    @Test
    fun `isCollectionOrArrayStatic List is collection`() {
        assertTrue(ReturnValueMutator.isCollectionOrArrayStatic(Type.getType(List::class.java)))
    }

    @Test
    fun `isCollectionOrArrayStatic ArrayList is collection`() {
        assertTrue(ReturnValueMutator.isCollectionOrArrayStatic(Type.getType(ArrayList::class.java)))
    }

    @Test
    fun `isCollectionOrArrayStatic LinkedList is collection`() {
        assertTrue(ReturnValueMutator.isCollectionOrArrayStatic(Type.getType(LinkedList::class.java)))
    }

    @Test
    fun `isCollectionOrArrayStatic Set is collection`() {
        assertTrue(ReturnValueMutator.isCollectionOrArrayStatic(Type.getType(Set::class.java)))
    }

    @Test
    fun `isCollectionOrArrayStatic HashSet is collection`() {
        assertTrue(ReturnValueMutator.isCollectionOrArrayStatic(Type.getType(HashSet::class.java)))
    }

    @Test
    fun `isCollectionOrArrayStatic TreeSet is collection`() {
        assertTrue(ReturnValueMutator.isCollectionOrArrayStatic(Type.getType(TreeSet::class.java)))
    }

    @Test
    fun `isCollectionOrArrayStatic Collection is collection`() {
        assertTrue(ReturnValueMutator.isCollectionOrArrayStatic(Type.getType(Collection::class.java)))
    }

    @Test
    fun `isCollectionOrArrayStatic Map is collection`() {
        assertTrue(ReturnValueMutator.isCollectionOrArrayStatic(Type.getType(Map::class.java)))
    }

    @Test
    fun `isCollectionOrArrayStatic HashMap is collection`() {
        assertTrue(ReturnValueMutator.isCollectionOrArrayStatic(Type.getType(HashMap::class.java)))
    }

    @Test
    fun `isCollectionOrArrayStatic TreeMap is collection`() {
        assertTrue(ReturnValueMutator.isCollectionOrArrayStatic(Type.getType(TreeMap::class.java)))
    }

    @Test
    fun `isCollectionOrArrayStatic kotlin collections prefix matches`() {
        assertTrue(ReturnValueMutator.isCollectionOrArrayStatic(Type.getType("Lkotlin/collections/MutableList;")))
    }

    @Test
    fun `isCollectionOrArrayStatic array is array`() {
        assertTrue(ReturnValueMutator.isCollectionOrArrayStatic(Type.getType("[I")))
    }

    @Test
    fun `isCollectionOrArrayStatic object array is array`() {
        assertTrue(ReturnValueMutator.isCollectionOrArrayStatic(Type.getType("[Ljava/lang/String;")))
    }

    @Test
    fun `isCollectionOrArrayStatic String is not collection or array`() {
        assertFalse(ReturnValueMutator.isCollectionOrArrayStatic(Type.getType(String::class.java)))
    }

    @Test
    fun `isCollectionOrArrayStatic Integer is not collection or array`() {
        assertFalse(ReturnValueMutator.isCollectionOrArrayStatic(Type.getType(Integer::class.java)))
    }

    @Test
    fun `isCollectionOrArrayStatic primitive int is not collection or array`() {
        assertFalse(ReturnValueMutator.isCollectionOrArrayStatic(Type.INT_TYPE))
    }

    @Test
    fun `isCollectionOrArrayStatic random class is not collection or array`() {
        assertFalse(ReturnValueMutator.isCollectionOrArrayStatic(Type.getType("Lcom/example/Foo;")))
    }
}
