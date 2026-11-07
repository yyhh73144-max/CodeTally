package com.github.yyhh73144max.codetally.data

import com.github.yyhh73144max.codetally.domain.analyzer.AnalyzerRegistry
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking

/**
 * [ProjectFileCollector] 与 [FileTextLoader] 的平台集成测试（轻量项目，内容根为 `temp:///src`）。
 */
class ProjectFileCollectorTest : BasePlatformTestCase() {

    /** 被测收集器。 */
    private val collector by lazy { ProjectFileCollector(project, AnalyzerRegistry.DEFAULT) }

    /** 项目级设置服务。 */
    private val settings get() = project.service<CodeTallyProjectSettings>()

    /** 轻量测试项目在用例间复用，先清空自定义排除，避免用例间相互影响。 */
    override fun tearDown() {
        try {
            settings.setExcludedFolders(emptyList())
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    /** 构造典型 Android 模块目录结构：Gradle 模块的 build 输出、名为 build 的源码包、隐藏缓存目录、不支持的扩展名。 */
    private fun createModuleLayout() {
        myFixture.addFileToProject("app/build.gradle.kts", "plugins {}\n")
        myFixture.addFileToProject("app/src/main/A.kt", "fun a() {}\n")
        myFixture.addFileToProject("app/src/main/res/layout.xml", "<a/>\n")
        myFixture.addFileToProject("app/build/generated/G.kt", "fun g() {}\n")
        myFixture.addFileToProject("app/notes.txt", "hello\n")
        myFixture.addFileToProject("lib/src/pkg/build/Real.java", "class Real {}\n")
        myFixture.addFileToProject(".gradle-user/caches/kotlin-dsl/Accessors.kt", "fun x() {}\n")
        myFixture.addFileToProject(".gradle-tmp/avd/config.xml", "<a/>\n")
        myFixture.addFileToProject("app/.cxx/Debug/cmake.cpp", "int main() {}\n")
        myFixture.addFileToProject("app/src/main/.hidden/Tool.kt", "fun t() {}\n")
    }

    /**
     * 验证整个项目统计：排除 Gradle 模块的 build 输出、所有隐藏目录（`.gradle-user` 等缓存）与不支持的扩展名，
     * 但保留名为 build 的源码包。回归风险：构建工具缓存被计入统计（曾出现 `.gradle-user` 中上千个文件被计入）。
     */
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

    /** 验证显式选中隐藏目录时尊重用户选择统计其内容，但内部更深的隐藏目录仍被排除。 */
    fun testExplicitHiddenDirectoryIsCounted() {
        createModuleLayout()
        myFixture.addFileToProject(".gradle-user/caches/.nested/Deep.kt", "fun d() {}\n")
        val hidden = myFixture.findFileInTempDir(".gradle-user")
        val names = runBlocking { collector.collect(listOf(hidden)) }.files.map { it.file.name }
        assertSameElements(names, "Accessors.kt")
    }

    /** 验证项目级自定义排除文件夹及其子目录在整个项目统计中被跳过，其他目录不受影响。 */
    fun testCustomExcludedFolderIsSkipped() {
        createModuleLayout()
        myFixture.addFileToProject("third_party/lib/Vendor.kt", "fun v() {}\n")
        myFixture.addFileToProject("third_party/lib/nested/Deep.cpp", "void d() {}\n")
        settings.toggleExcluded(listOf(myFixture.findFileInTempDir("third_party")))
        val names = runBlocking { collector.collect(null) }.files.map { it.file.name }.toSet()
        assertEquals(setOf("build.gradle.kts", "A.kt", "layout.xml", "Real.java"), names)
    }

    /**
     * 验证排除目录下存在独立内容根（如 Gradle includeBuild 的 `third_party/dexkit`）时，其中文件仍被排除。
     * 回归风险：祖先链检查在最近的内容根处停止，导致外层被排除的 `third_party` 失效（曾在真实项目中出现）。
     */
    fun testExclusionAppliesAcrossNestedContentRoot() {
        createModuleLayout()
        myFixture.addFileToProject("third_party/dexkit/src/Lib.kt", "fun l() {}\n")
        myFixture.addFileToProject("third_party/dexkit/.cache/Hidden.kt", "fun h() {}\n")
        val nestedRoot = myFixture.findFileInTempDir("third_party/dexkit")
        PsiTestUtil.addContentRoot(module, nestedRoot)
        try {
            // 未排除时：嵌套内容根下的普通文件计入，其内部隐藏目录仍被排除。
            val before = runBlocking { collector.collect(null) }.files.map { it.file.name }.toSet()
            assertTrue("Lib.kt" in before)
            assertFalse("Hidden.kt" in before)
            settings.toggleExcluded(listOf(myFixture.findFileInTempDir("third_party")))
            val after = runBlocking { collector.collect(null) }.files.map { it.file.name }.toSet()
            assertFalse("Lib.kt" in after)
        } finally {
            PsiTestUtil.removeContentEntry(module, nestedRoot)
        }
    }

    /** 验证显式选中被自定义排除的文件夹时仍统计其内容，与隐藏目录的“显式选择优先”规则一致。 */
    fun testExplicitSelectionOverridesCustomExclusion() {
        createModuleLayout()
        val srcDir = myFixture.findFileInTempDir("app/src/main")
        settings.toggleExcluded(listOf(srcDir))
        val names = runBlocking { collector.collect(listOf(srcDir)) }.files.map { it.file.name }
        assertSameElements(names, "A.kt", "layout.xml")
    }

    /** 验证切换排除：未排除时加入、全部已排除时移除，且存储路径可解析回原目录（回归风险：路径换算不可逆导致设置失效）。 */
    fun testToggleAndResolveRoundTrip() {
        createModuleLayout()
        val dir = myFixture.findFileInTempDir("app/src")
        settings.toggleExcluded(listOf(dir))
        assertTrue(settings.isExcluded(dir))
        assertEquals(dir, settings.resolve(settings.excludedFolders.single()))
        settings.toggleExcluded(listOf(dir))
        assertFalse(settings.isExcluded(dir))
        assertEmpty(settings.excludedFolders)
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
