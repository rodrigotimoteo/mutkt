package com.github.rodrigotimoteo.mutation.report

import com.github.rodrigotimoteo.mutation.model.Mutation
import com.github.rodrigotimoteo.mutation.model.MutationReport
import com.github.rodrigotimoteo.mutation.model.MutationResult
import com.github.rodrigotimoteo.mutation.model.MutationStatus
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MutationGraphGeneratorTest {
    @Test
    fun `generate produces HTML file at outputDir`(
        @TempDir tempDir: Path,
    ) {
        val report = createReport()
        val file = MutationGraphGenerator.generate(report, tempDir.toFile())
        assertNotNull(file)
        assertTrue(file.exists(), "Generated file should exist")
        assertTrue(file.name.endsWith(".html"))
    }

    @Test
    fun `generate produces HTML file with name mutation-graph html`(
        @TempDir tempDir: Path,
    ) {
        val report = createReport()
        val file = MutationGraphGenerator.generate(report, tempDir.toFile())
        assertEquals("mutation-graph.html", file.name)
    }

    @Test
    fun `generated HTML starts with DOCTYPE`(
        @TempDir tempDir: Path,
    ) {
        val report = createReport()
        val file = MutationGraphGenerator.generate(report, tempDir.toFile())
        val content = file.readText()
        assertTrue(content.startsWith("<!DOCTYPE html>"), "Should start with DOCTYPE")
    }

    @Test
    fun `generated HTML contains title Mutation Test Graph`(
        @TempDir tempDir: Path,
    ) {
        val report = createReport()
        val file = MutationGraphGenerator.generate(report, tempDir.toFile())
        val content = file.readText()
        assertTrue(content.contains("Mutation Test Graph"), "Should contain title")
    }

    @Test
    fun `generated HTML contains D3 script tag`(
        @TempDir tempDir: Path,
    ) {
        val report = createReport()
        val file = MutationGraphGenerator.generate(report, tempDir.toFile())
        val content = file.readText()
        assertTrue(content.contains("d3"), "Should reference d3.js")
    }

    @Test
    fun `generated HTML includes KILLED mutations as nodes`(
        @TempDir tempDir: Path,
    ) {
        val mutation = createMutation(id = "m1", className = "com.example.Foo")
        val result = MutationResult(mutation, MutationStatus.KILLED, 50L)
        val report = MutationReport(listOf(result), 1, 1, 0, 0, 0, 0, 50L)
        val file = MutationGraphGenerator.generate(report, tempDir.toFile())
        val content = file.readText()
        assertTrue(content.contains("m1"), "Should include mutation id")
        assertTrue(content.contains("com.example.Foo"), "Should include class name")
    }

    @Test
    fun `generated HTML includes multiple mutations`(
        @TempDir tempDir: Path,
    ) {
        val results =
            listOf(
                MutationResult(createMutation(id = "m1", className = "com.Foo"), MutationStatus.KILLED, 50L),
                MutationResult(createMutation(id = "m2", className = "com.Foo"), MutationStatus.SURVIVED, 30L),
                MutationResult(createMutation(id = "m3", className = "com.Bar"), MutationStatus.KILLED, 20L),
            )
        val report = MutationReport(results, 3, 2, 1, 0, 0, 0, 100L)
        val file = MutationGraphGenerator.generate(report, tempDir.toFile())
        val content = file.readText()
        assertTrue(content.contains("m1"))
        assertTrue(content.contains("m2"))
        assertTrue(content.contains("m3"))
        assertTrue(content.contains("com.Foo"))
        assertTrue(content.contains("com.Bar"))
    }

    @Test
    fun `generate on empty report still produces valid HTML`(
        @TempDir tempDir: Path,
    ) {
        val report = MutationReport(emptyList(), 0, 0, 0, 0, 0, 0, 0L)
        val file = MutationGraphGenerator.generate(report, tempDir.toFile())
        val content = file.readText()
        assertTrue(content.startsWith("<!DOCTYPE html>"), "expected DOCTYPE for empty report")
        assertTrue(content.contains("Mutation Test Graph"), "expected title for empty report")
    }

    @Test
    fun `generate on error status includes status`(
        @TempDir tempDir: Path,
    ) {
        val mutation = createMutation(id = "m1", className = "com.Foo")
        val result = MutationResult(mutation, MutationStatus.ERROR, 100L, "boom")
        val report = MutationReport(listOf(result), 1, 0, 0, 1, 0, 0, 100L)
        val file = MutationGraphGenerator.generate(report, tempDir.toFile())
        val content = file.readText()
        assertTrue(content.contains("m1"))
    }

    @Test
    fun `generate writes file to specific outputDir`(
        @TempDir tempDir: Path,
    ) {
        val subDir = tempDir.resolve("subdir").toFile()
        subDir.mkdir()
        val report = createReport()
        val file = MutationGraphGenerator.generate(report, subDir)
        assertTrue(file.absolutePath.contains("subdir"), "File should be in subdir, got: ${file.absolutePath}")
        assertTrue(file.exists(), "expected graph file at ${file.absolutePath}")
    }

    @Test
    fun `generated HTML has style block`(
        @TempDir tempDir: Path,
    ) {
        val report = createReport()
        val file = MutationGraphGenerator.generate(report, tempDir.toFile())
        val content = file.readText()
        assertTrue(content.contains("<style>"), "Should include style block")
    }

    @Test
    fun `generated HTML has script block`(
        @TempDir tempDir: Path,
    ) {
        val report = createReport()
        val file = MutationGraphGenerator.generate(report, tempDir.toFile())
        val content = file.readText()
        assertTrue(content.contains("<script>"), "Should include script block")
    }

    @Test
    fun `generated HTML escapes special characters in graph nodes`(
        @TempDir tempDir: Path,
    ) {
        val mutation = createMutation(id = "m<1>", className = "com.foo</script>bar")
        val result = MutationResult(mutation, MutationStatus.KILLED, 50L)
        val report = MutationReport(listOf(result), 1, 1, 0, 0, 0, 0, 50L)
        val file = MutationGraphGenerator.generate(report, tempDir.toFile())
        val content = file.readText()
        // The graph generator escapes </ to <\/ to prevent breaking out
        // of the inline <script> block.
        assertTrue(
            content.contains("<\\/script>"),
            "expected </script> to be escaped to <\\/script> in graph output",
        )
        // Raw </script> must not appear inside the graph data block.
        val dataStart = content.indexOf("const nodesData = [")
        val dataEnd = content.indexOf("];", dataStart)
        val dataBlock = content.substring(dataStart, dataEnd)
        assertTrue(
            !dataBlock.contains("</script>"),
            "raw </script> must not appear in graph data block, got: $dataBlock",
        )
    }

    private fun createReport(): MutationReport {
        val mutation = createMutation(id = "m1", className = "com.example.Foo")
        val result = MutationResult(mutation, MutationStatus.KILLED, 50L)
        return MutationReport(listOf(result), 1, 1, 0, 0, 0, 0, 50L)
    }

    private fun createMutation(
        id: String,
        className: String,
    ): Mutation {
        return Mutation(
            id = id,
            className = className,
            methodName = "test",
            methodDescriptor = "()I",
            operator = MutationOperator.ARITHMETIC,
            lineNumber = 10,
            originalBytecode = byteArrayOf(),
            mutatedBytecode = byteArrayOf(),
            description = "test",
        )
    }
}
