package com.repflow.app.architecture

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * A lightweight guardrail for the layered architecture documented in
 * docs/PRODUCT_AND_ARCHITECTURE.md - **not** complete architectural
 * enforcement. It parses only `package` and `import` declarations by
 * scanning source text, so it cannot see reflection, generated code, or
 * transitive dependencies. Real enforcement arrives once Domain, Application,
 * Data and Infrastructure are extracted into separate Gradle modules (see ADR
 * 0003 and plan.md decision D-15); until then this test exists to catch
 * accidental, source-visible boundary violations early.
 *
 * Only production sources (`src/main/kotlin`) are checked. Test sources are
 * allowed to reference any layer.
 *
 * Rules are added incrementally, layer by layer, as each layer gains real
 * code (see plan.md checkpoints 3-7): `domain`, `presentation`,
 * `application`, `data` and `infrastructure` exist so far.
 */
class LayerBoundaryTest {
    private val basePackage = "com.repflow.app"
    private val mainKotlinRoot: File by lazy { findMainKotlinRoot() }

    @Test
    fun `layers already introduced in this milestone exist and contain source files`() {
        val introducedLayers = listOf("domain", "presentation", "application", "data", "infrastructure")
        val missingOrEmpty = introducedLayers.filter { layer -> layerSourceFiles(layer).isEmpty() }

        assertTrue(
            "Expected every already-introduced layer directory to exist and contain at least one " +
                ".kt file so this guardrail cannot pass vacuously: $missingOrEmpty",
            missingOrEmpty.isEmpty(),
        )
    }

    @Test
    fun `domain does not import Android, Jetpack, DI, coroutines, serialization, or other layers`() {
        assertNoForbiddenImports(
            layer = "domain",
            ruleName = "domain purity",
            forbiddenPrefixes =
                listOf(
                    "android.",
                    "androidx.",
                    "dagger.",
                    "javax.inject.",
                    "kotlinx.coroutines.",
                    "kotlinx.serialization.",
                    "$basePackage.application.",
                    "$basePackage.data.",
                    "$basePackage.infrastructure.",
                    "$basePackage.presentation.",
                ),
        )
    }

    @Test
    fun `presentation does not import infrastructure, Room, or android database directly`() {
        assertNoForbiddenImports(
            layer = "presentation",
            ruleName = "presentation boundary",
            forbiddenPrefixes =
                listOf(
                    "$basePackage.infrastructure.",
                    "androidx.room.",
                    "android.database.",
                ),
        )
    }

    @Test
    fun `application stays framework-free besides DI annotations and coroutines Flow`() {
        assertNoForbiddenImports(
            layer = "application",
            ruleName = "application boundary",
            forbiddenPrefixes =
                listOf(
                    "android.",
                    "androidx.",
                    "dagger.",
                    "kotlinx.serialization.",
                    "$basePackage.data.",
                    "$basePackage.infrastructure.",
                    "$basePackage.presentation.",
                ),
        )
    }

    @Test
    fun `data does not import presentation`() {
        assertNoForbiddenImports(
            layer = "data",
            ruleName = "data boundary",
            forbiddenPrefixes = listOf("$basePackage.presentation."),
        )
    }

    @Test
    fun `infrastructure does not import presentation`() {
        assertNoForbiddenImports(
            layer = "infrastructure",
            ruleName = "infrastructure boundary",
            forbiddenPrefixes = listOf("$basePackage.presentation."),
        )
    }

    private fun assertNoForbiddenImports(
        layer: String,
        ruleName: String,
        forbiddenPrefixes: List<String>,
    ) {
        val files = layerSourceFiles(layer)
        if (files.isEmpty()) {
            fail("Expected at least one .kt file under layer '$layer' to check $ruleName against")
        }

        val violations =
            files.flatMap { file ->
                parseHeader(file).imports.mapNotNull { (line, import) ->
                    val violatedPrefix = forbiddenPrefixes.firstOrNull { import.startsWith(it) }
                    violatedPrefix?.let { "$file:$line — $import violates $ruleName" }
                }
            }

        assertTrue(violations.joinToString(separator = "\n"), violations.isEmpty())
    }

    private fun layerSourceFiles(layer: String): List<File> {
        val dir = File(mainKotlinRoot, "com/repflow/app/$layer")
        if (!dir.isDirectory) return emptyList()
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun findMainKotlinRoot(): File {
        var dir = File(".").absoluteFile
        repeat(6) {
            val fromRepoRoot = File(dir, "app/src/main/kotlin")
            if (fromRepoRoot.isDirectory) return fromRepoRoot
            val fromModuleRoot = File(dir, "src/main/kotlin")
            if (fromModuleRoot.isDirectory) return fromModuleRoot
            dir = dir.parentFile ?: return@repeat
        }
        error("Could not locate src/main/kotlin starting from working directory ${File(".").absolutePath}")
    }

    /** A source file's `package` line and every `import` line, in file order, with 1-based line numbers. */
    private data class FileHeader(
        val packageName: String?,
        val imports: List<Pair<Int, String>>,
    )

    private sealed interface HeaderLine {
        data object Blank : HeaderLine

        data object FileAnnotation : HeaderLine

        data class Package(
            val name: String,
        ) : HeaderLine

        data class Import(
            val resolvedName: String,
        ) : HeaderLine

        data object EndOfHeader : HeaderLine
    }

    /**
     * Scans from the top of [file], stopping at the first top-level
     * declaration. Comments (`//` and single-level `/* ... */`) are stripped
     * first so that commented-out or string-embedded text is never mistaken
     * for a real import. Import aliases (`import a.b.C as D`) resolve to the
     * aliased fully-qualified name.
     */
    private fun parseHeader(file: File): FileHeader {
        val lines = stripComments(file.readText()).lines()
        var packageName: String? = null
        val imports = mutableListOf<Pair<Int, String>>()

        for ((index, rawLine) in lines.withIndex()) {
            when (val headerLine = classifyHeaderLine(rawLine.trim())) {
                is HeaderLine.Package -> packageName = headerLine.name
                is HeaderLine.Import -> imports += (index + 1) to headerLine.resolvedName
                HeaderLine.Blank, HeaderLine.FileAnnotation -> Unit
                HeaderLine.EndOfHeader -> break
            }
        }
        return FileHeader(packageName, imports)
    }

    private fun classifyHeaderLine(line: String): HeaderLine =
        when {
            line.isEmpty() -> {
                HeaderLine.Blank
            }

            line.startsWith("@file:") -> {
                HeaderLine.FileAnnotation
            }

            line.startsWith("package ") -> {
                HeaderLine.Package(line.removePrefix("package ").trim().trimEnd(';'))
            }

            line.startsWith("import ") -> {
                HeaderLine.Import(resolveImport(line.removePrefix("import ")))
            }

            else -> {
                HeaderLine.EndOfHeader
            }
        }

    private fun resolveImport(importBody: String): String {
        val body = importBody.trim().trimEnd(';')
        val aliasIndex = body.indexOf(" as ")
        return if (aliasIndex >= 0) body.substring(0, aliasIndex).trim() else body
    }

    /**
     * Strips `/* ... */` block comments (non-nested - real nesting is rare
     * enough in practice that this guardrail does not need to support it)
     * and then `//` line comments, while preserving line numbers so
     * violation messages point at the original source line.
     */
    private fun stripComments(text: String): String {
        val withoutBlockComments =
            BLOCK_COMMENT.replace(text) { match -> "\n".repeat(match.value.count { it == '\n' }) }
        return withoutBlockComments.lineSequence().joinToString("\n") { stripLineComment(it) }
    }

    private fun stripLineComment(line: String): String {
        var inString = false
        var index = 0
        while (index < line.length) {
            val c = line[index]
            when {
                inString && c == '\\' -> index++
                c == '"' -> inString = !inString
                !inString && c == '/' && line.getOrNull(index + 1) == '/' -> return line.substring(0, index)
            }
            index++
        }
        return line
    }

    private companion object {
        val BLOCK_COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
    }
}
