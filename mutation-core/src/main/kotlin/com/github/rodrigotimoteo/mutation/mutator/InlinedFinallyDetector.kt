package com.github.rodrigotimoteo.mutation.mutator

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TryCatchBlockNode

/**
 * Detects inlined finally blocks in bytecode.
 *
 * When the Kotlin/JVM compiler inlines finally blocks, it duplicates the
 * finally block code at each exit point. This detector identifies such
 * duplications to avoid creating redundant mutations.
 */
class InlinedFinallyDetector {
    /**
     * Represents an inlined finally block.
     */
    data class InlinedFinallyBlock(
        val methodName: String,
        val startLine: Int,
        val endLine: Int,
        val handlerLine: Int,
        // Lines where finally block is duplicated
        val duplicatedAt: List<Int>,
    )

    /**
     * Detect inlined finally blocks in a class.
     *
     * @param classBytes The class bytecode
     * @return List of inlined finally blocks
     */
    fun detect(classBytes: ByteArray): List<InlinedFinallyBlock> {
        val classReader = ClassReader(classBytes)
        val classNode = ClassNode()
        classReader.accept(classNode, 0)

        val inlinedBlocks = mutableListOf<InlinedFinallyBlock>()

        for (methodNode in classNode.methods) {
            val blocks = detectInMethod(methodNode)
            inlinedBlocks.addAll(blocks)
        }

        return inlinedBlocks
    }

    /**
     * Detect inlined finally blocks in a method.
     */
    private fun detectInMethod(methodNode: MethodNode): List<InlinedFinallyBlock> {
        val inlinedBlocks = mutableListOf<InlinedFinallyBlock>()

        // Find finally handlers (TryCatchBlock with null type)
        val finallyHandlers =
            methodNode.tryCatchBlocks?.filter { block ->
                block.type == null // null type = finally handler
            } ?: return inlinedBlocks

        for (handler in finallyHandlers) {
            val block = analyzeFinallyBlock(methodNode, handler)
            if (block != null) {
                inlinedBlocks.add(block)
            }
        }

        return inlinedBlocks
    }

    /**
     * Analyze a finally block for inlining.
     */
    private fun analyzeFinallyBlock(
        methodNode: MethodNode,
        handler: TryCatchBlockNode,
    ): InlinedFinallyBlock? {
        // Get line numbers for the handler
        val handlerStart = getLineNumber(methodNode, handler.handler)
        val handlerEnd = findHandlerEnd(methodNode, handler.handler)

        if (handlerStart == null || handlerEnd == null) {
            return null
        }

        // Find all exit points (where finally block is duplicated)
        val duplicatedAt = findDuplicatedExitPoints(methodNode, handler)

        // Only report if there are duplications (indicating inlining)
        if (duplicatedAt.size <= 1) {
            return null
        }

        return InlinedFinallyBlock(
            methodName = methodNode.name,
            startLine = handlerStart,
            endLine = handlerEnd,
            handlerLine = handlerStart,
            duplicatedAt = duplicatedAt,
        )
    }

    /**
     * Get line number for an instruction.
     */
    private fun getLineNumber(
        methodNode: MethodNode,
        label: LabelNode,
    ): Int? {
        val instructions = methodNode.instructions?.toArray() ?: return null

        for (insn in instructions) {
            if (insn is org.objectweb.asm.tree.LineNumberNode) {
                // Check if this line number node references the label
                val start = insn.start
                if (start == label) {
                    return insn.line
                }
            }
        }

        return null
    }

    /**
     * Find the end line of a handler.
     */
    private fun findHandlerEnd(
        methodNode: MethodNode,
        handlerStart: LabelNode,
    ): Int? {
        val instructions = methodNode.instructions?.toArray() ?: return null
        var startFound = false
        var lastLineNumber: Int? = null

        for (insn in instructions) {
            when {
                insn is org.objectweb.asm.tree.LineNumberNode -> {
                    if (insn.start == handlerStart) {
                        startFound = true
                        lastLineNumber = insn.line
                    } else if (startFound) {
                        return lastLineNumber
                    }
                }
                startFound && insn is org.objectweb.asm.tree.InsnNode -> {
                    val opcode = insn.opcode
                    if (opcode in
                        listOf(
                            org.objectweb.asm.Opcodes.RETURN,
                            org.objectweb.asm.Opcodes.ARETURN,
                            org.objectweb.asm.Opcodes.IRETURN,
                            org.objectweb.asm.Opcodes.LRETURN,
                            org.objectweb.asm.Opcodes.FRETURN,
                            org.objectweb.asm.Opcodes.DRETURN,
                            org.objectweb.asm.Opcodes.ATHROW,
                        )
                    ) {
                        return lastLineNumber
                    }
                }
            }
        }

        return lastLineNumber
    }

    /**
     * Find duplicated exit points where finally block is copied.
     */
    private fun findDuplicatedExitPoints(
        methodNode: MethodNode,
        handler: TryCatchBlockNode,
    ): List<Int> {
        val exitPoints = mutableListOf<Int>()
        val instructions = methodNode.instructions?.toArray() ?: return exitPoints

        // Find all return/throw instructions that jump to this handler
        for (insn in instructions) {
            if (insn is org.objectweb.asm.tree.JumpInsnNode) {
                // Check if this jump targets the handler
                if (insn.label == handler.handler) {
                    val line = getLineNumber(methodNode, insn.label)
                    if (line != null) {
                        exitPoints.add(line)
                    }
                }
            } else if (insn is org.objectweb.asm.tree.InsnNode) {
                val opcode = insn.opcode
                if (opcode in
                    listOf(
                        org.objectweb.asm.Opcodes.RETURN,
                        org.objectweb.asm.Opcodes.ARETURN,
                        org.objectweb.asm.Opcodes.IRETURN,
                        org.objectweb.asm.Opcodes.LRETURN,
                        org.objectweb.asm.Opcodes.FRETURN,
                        org.objectweb.asm.Opcodes.DRETURN,
                        org.objectweb.asm.Opcodes.ATHROW,
                    )
                ) {
                    val line = getLineNumber(methodNode, insn)
                    if (line != null) {
                        exitPoints.add(line)
                    }
                }
            }
        }

        return exitPoints
    }

    /**
     * Get line number for an instruction.
     */
    private fun getLineNumber(
        methodNode: MethodNode,
        insn: org.objectweb.asm.tree.AbstractInsnNode,
    ): Int? {
        var current = insn
        while (current != null) {
            if (current is org.objectweb.asm.tree.LineNumberNode) {
                return current.line
            }
            current = current.previous
        }
        return null
    }

    /**
     * Check if a mutation is within an inlined finally block.
     */
    fun isInInlinedBlock(
        mutationLine: Int,
        inlinedBlocks: List<InlinedFinallyBlock>,
    ): Boolean {
        return inlinedBlocks.any { block ->
            mutationLine in block.startLine..block.endLine
        }
    }

    /**
     * Get the number of mutations that would be skipped due to inlined finally blocks.
     */
    fun countSkippedMutations(
        mutations: List<MutationInfo>,
        inlinedBlocks: List<InlinedFinallyBlock>,
    ): Int {
        return mutations.count { mutation ->
            isInInlinedBlock(mutation.lineNumber, inlinedBlocks)
        }
    }
}
