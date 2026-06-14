package com.github.rodrigotimoteo.mutation.mutator

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Resolves class references for the CommonSuperClassClassWriter.
 * Caches results and can define a single project class on the fly.
 */
internal class LoadClassResolver(
    selfName: String?,
    classBytes: ByteArray,
) {
    private val classCache = mutableMapOf<String, Class<*>>()
    private val projectClassBytes: Map<String, ByteArray> =
        if (selfName != null) mapOf(selfName to classBytes) else emptyMap()
    private val projectClassLoader =
        object : ClassLoader(Mutator::class.java.classLoader) {
            fun defineProjectClass(
                binary: String,
                bytes: ByteArray,
            ): Class<*> = defineClass(binary, bytes, 0, bytes.size)
        }

    fun resolve(type: String): Class<*>? {
        classCache[type]?.let { return it }
        val binary = type.replace('/', '.')
        val result: Class<*>? =
            try {
                projectClassBytes[type]?.let { bytes ->
                    projectClassLoader.defineProjectClass(binary, bytes)
                } ?: Class.forName(
                    binary,
                    false,
                    Thread.currentThread().contextClassLoader ?: Mutator::class.java.classLoader,
                )
            } catch (e: Throwable) {
                null
            }
        if (result != null) classCache[type] = result
        return result
    }
}

/**
 * ClassWriter that delegates getCommonSuperClass type resolution to a
 * [LoadClassResolver], enabling common-super computation for project types.
 */
internal class CommonSuperClassClassWriter(
    private val resolver: LoadClassResolver,
) : ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES) {
    override fun getCommonSuperClass(
        type1: String,
        type2: String,
    ): String {
        if (type1 == type2) return type1
        if (type1 == "java/lang/Object" || type2 == "java/lang/Object") {
            return "java/lang/Object"
        }

        val c1 =
            try {
                resolver.resolve(type1)
            } catch (e: Throwable) {
                null
            }
        val c2 =
            try {
                resolver.resolve(type2)
            } catch (e: Throwable) {
                null
            }

        if (c1 == null && c2 == null) return "java/lang/Object"
        if (c1 == null) return type2
        if (c2 == null) return type1

        // Walk class hierarchy of c1
        var t: Class<*>? = c1
        while (t != null) {
            if (t.isAssignableFrom(c2)) return t.name.replace('.', '/')
            t = t.superclass
        }

        // Walk class hierarchy of c2
        t = c2
        while (t != null) {
            if (t.isAssignableFrom(c1)) return t.name.replace('.', '/')
            t = t.superclass
        }

        // Walk interface hierarchy — find common interface
        val c1Interfaces = mutableSetOf<Class<*>>()
        t = c1
        while (t != null) {
            c1Interfaces.addAll(t.interfaces)
            t = t.superclass
        }

        t = c2
        while (t != null) {
            for (iface in t.interfaces) {
                if (iface in c1Interfaces) return iface.name.replace('.', '/')
            }
            t = t.superclass
        }

        return "java/lang/Object"
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

private val COLLECTION_INTERFACES =
    setOf(
        "java.util.List",
        "java.util.Set",
        "java.util.Map",
    )

internal object ReturnValueMutator {
    fun isCollectionOrArrayStatic(type: Type): Boolean {
        if (type.sort == Type.ARRAY) return true
        val cn = type.className
        // Type.className returns dotted form (java.util.List), not slashed.
        // Restrict to interfaces + arrays to keep scanner aligned with what
        // applyEmptyReturn can safely produce.
        return cn in COLLECTION_INTERFACES ||
            cn.startsWith("java.util.List") ||
            cn.startsWith("java.util.Set") ||
            cn.startsWith("java.util.Map") ||
            cn.startsWith("kotlin.collections.")
    }
}
