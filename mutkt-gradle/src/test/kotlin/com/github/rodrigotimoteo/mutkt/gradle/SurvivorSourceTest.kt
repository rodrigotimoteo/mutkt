package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.asm.AsmMutantGenerator
import com.github.rodrigotimoteo.mutkt.domain.MutantResult
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus.SURVIVED
import com.github.rodrigotimoteo.mutkt.domain.MutationReport
import com.github.rodrigotimoteo.mutkt.report.ConsoleReportWriter
import com.github.rodrigotimoteo.mutkt.report.FileSourceLookup
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Survivor snippets of the text report come from the file named by the compiled class, not from its class name. */
class SurvivorSourceTest {
    @TempDir
    lateinit var root: Path

    private val decoy = "decoy one\ndecoy two\ndecoy three\ndecoy four\n"

    private fun write(
        relative: String,
        text: String,
    ) {
        val file = root.resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }

    private fun report(
        fileName: String,
        source: String,
    ): String {
        val classes = compileKotlin(source, fileName = fileName).map { ClassFile(it.key, it.value) }
        val generator =
            AsmMutantGenerator { name -> ClassLoader.getSystemResourceAsStream("$name.class")?.use { it.readBytes() } }
        val results = generator.discover(classes).map { MutantResult(it, SURVIVED) }
        return StringBuilder()
            .also {
                ConsoleReportWriter(
                    it,
                    FileSourceLookup(listOf(root)),
                ).write(MutationReport(results))
            }.toString()
    }

    private val prices = "package shop\n\nfun total(a: Int, b: Int) = a + b\n"

    @Test
    fun `file facade shows the line of its source file`() {
        write("shop/Prices.kt", prices)
        write("shop/PricesKt.kt", decoy)
        val text = report("Prices.kt", prices)
        assertTrue("shop.PricesKt.total(II)I (shop.PricesKt:3)" in text, text)
        assertTrue(Regex(">\\s+3 \\| fun total\\(a: Int, b: Int\\) = a \\+ b").containsMatchIn(text), text)
        assertFalse("decoy" in text, text)
    }

    @Test
    fun `JvmName facade shows the line of its source file`() {
        val source = "@file:JvmName(\"Money\")\n\npackage shop\n\nfun total(a: Int, b: Int) = a + b\n"
        write("shop/Prices.kt", source)
        write("shop/Money.kt", decoy)
        val text = report("Prices.kt", source)
        assertTrue("shop.Money.total(II)I (shop.Money:5)" in text, text)
        assertTrue("fun total(a: Int, b: Int) = a + b" in text, text)
        assertFalse("decoy" in text, text)
    }

    @Test
    fun `class declared in a differently named file shows the line of that file`() {
        val source = "package shop\n\nclass Order {\n    fun total(a: Int, b: Int) = a + b\n}\n"
        write("shop/Items.kt", source)
        write("shop/Order.kt", decoy)
        val text = report("Items.kt", source)
        assertTrue("shop.Order.total(II)I (shop.Order:4)" in text, text)
        assertTrue("    fun total(a: Int, b: Int) = a + b" in text, text)
        assertFalse("decoy" in text, text)
    }

    @Test
    fun `missing source leaves the location without a snippet`() {
        write("shop/PricesKt.kt", decoy)
        val text = report("Prices.kt", prices)
        assertTrue("shop.PricesKt.total(II)I (shop.PricesKt:3)" in text, text)
        assertFalse(" | " in text, text)
        assertEquals(1, Regex("Survivors \\(").findAll(text).count())
    }
}
