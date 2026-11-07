package com.github.yyhh73144max.codetally.data

import com.github.yyhh73144max.codetally.domain.analyzer.AnalyzerRegistry
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking

/**
 * [ProjectFileCollector] 与 [FileTextLoader] 的平台集成测试（轻量项目，内容根为 `temp:///src`）。
 */
class ProjectFileCollectorTest : BasePlatformTestCase() {

    /** 被测收集器。 */
    private val collector by lazy { ProjectFileCollector(project, AnalyzerRegistry.DEFAULT) }

    /** 构造典型 Android 模块目录结构：Gradle 模块的 build 输出、名为 build 的源码包、不支持的扩展名。 */
    private fun createModuleLayout() {
        myFixture.addFileToProject("app/build.gradle.kts", "plugins {}\n")
        myFixture.addFileToProject("app/src/main/A.kt", "fun a() {}\n")
        myFixture.addFileToProject("app/src/main/res/layout.xml", "<a/>\n")
        myFixture.addFileToProject("app/build/generated/G.kt", "fun g() {}\n")
        myFixture.addFileToProject("app/notes.txt", "hello\n")
        myFixture.addFileToProject("lib/src/pkg/build/Real.java", "class Real {}\n")
    }

    /** 验证整个项目统计：排除 Gradle 模块的 build 输出与不支持的扩展名，但保留名为 build 的源码包。 */
    fun testWholeProjectExcludesGradleOutputOnly() {
        createModuleLayout()
        val names = runBlocking { collector.collect(null) }.files.map { it.file.name }.toSet()
        assertEquals(setOf("build.gradle.kts", "A.kt", "layout.xml", "Real.java"), names)
    }

    /** 验证选中范围只统计选中目录，且目录与其内部文件同时选中时结果去重。 */
    fun testSelectionIsLimitedAndDeduplicated() {
        createModuleLayout()
        val srcDir = myFixture.findFileInTempDir("app/src/main")
        val kotlinFile = myFixture.findFileInTempDir("app/src/main/A.kt")
        val names = runBlocking { collector.collect(listOf(srcDir, kotlinFile)) }.files.map { it.file.name }
        assertSameElements(names, "A.kt", "layout.xml")
    }

    /** 验证显式选中的不支持文件被忽略，回归风险：右键单个 txt 文件时不应产生统计行。 */
    fun testExplicitUnsupportedFileIsIgnored() {
        createModuleLayout()
        val txt = myFixture.findFileInTempDir("app/notes.txt")
        assertEmpty(runBlocking { collector.collect(listOf(txt)) }.files)
    }

    /** 验证未保存的编辑器内容优先于磁盘内容，使统计与用户所见一致。 */
    fun testUnsavedDocumentTextWins() {
        val file = myFixture.addFileToProject("B.kt", "fun a() {}\n").virtualFile
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText("fun a() {}\nfun b() {}\n") }
        val loader = FileTextLoader()
        val unsaved = runBlocking { loader.snapshotUnsavedDocuments() }
        assertEquals("fun a() {}\nfun b() {}\n", loader.load(file, unsaved).toString())
    }
}
