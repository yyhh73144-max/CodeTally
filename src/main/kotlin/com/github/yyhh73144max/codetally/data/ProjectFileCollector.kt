package com.github.yyhh73144max.codetally.data

import com.github.yyhh73144max.codetally.domain.analyzer.AnalyzerRegistry
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.GeneratedSourcesFilter
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

/**
 * 待统计的文件及其展示路径。
 *
 * @property file 源文件。
 * @property displayPath 相对项目根目录的路径；文件不在项目目录下时为完整路径。
 */
class CollectedFile(val file: VirtualFile, val displayPath: String)

/**
 * 文件收集结果。
 *
 * @property files 需要分析的文件，已去重，顺序与遍历顺序一致。
 * @property oversizedCount 因超过 [ProjectFileCollector.MAX_FILE_BYTES] 被跳过的受支持文件数。
 */
class CollectedFiles(val files: List<CollectedFile>, val oversizedCount: Int)

/**
 * 项目文件收集器：IDE 项目模型与 VFS 的边界适配层，决定“哪些文件参与统计”。
 *
 * 规则：
 * - 只遍历项目内容根（`ProjectFileIndex`），自动排除库、SDK 与被标记为 excluded 的目录；
 * - 排除 `.gradle`、`.idea` 目录，以及 Gradle 模块（同级存在 `build.gradle(.kts)`/`settings.gradle(.kts)`）下的
 *   `build`、`.cxx`、`.externalNativeBuild` 输出目录——只按名称排除会误伤名为 `build` 的源码包，因此要求同级有 Gradle 脚本；
 * - 排除任意 `GeneratedSourcesFilter` 认定的生成源码（Android 插件会注册此过滤器）；
 * - 只保留 [AnalyzerRegistry] 支持的扩展名，排除二进制类型与超过大小上限的文件；
 * - 用户显式选中的单个文件只校验扩展名、类型与大小，尊重用户的明确选择。
 *
 * 线程：[collect] 为挂起函数，在可取消的读操作中执行；遇到写操作排队时读操作会被平台中断并重试，
 * 因此遍历过程必须无副作用，每次重试都会重新构建结果。
 *
 * @param project 当前项目。
 * @param registry 用于判断扩展名是否受支持。
 */
class ProjectFileCollector(private val project: Project, private val registry: AnalyzerRegistry) {

    /**
     * 收集待统计文件。
     *
     * @param roots 统计范围；null 表示整个项目内容，否则为用户选中的文件/目录（可相互嵌套，结果自动去重）。
     * @return 收集结果。
     * @throws kotlinx.coroutines.CancellationException 协程被取消时抛出。
     */
    suspend fun collect(roots: List<VirtualFile>?): CollectedFiles = readAction { collectUnderReadLock(roots) }

    /** 在读锁内执行实际遍历；每个文件都检查取消，保证长时间遍历可及时响应取消与写操作。 */
    private fun collectUnderReadLock(roots: List<VirtualFile>?): CollectedFiles {
        val fileIndex = ProjectFileIndex.getInstance(project)
        val baseDir = project.guessProjectDir()
        val accepted = LinkedHashMap<VirtualFile, CollectedFile>()
        val oversized = HashSet<VirtualFile>()
        // 目录跳过结果按目录缓存：同一目录下的大量文件只需判断一次祖先链。
        // 注意：平台的 iterateContent(processor, filter) 不会因 filter 对目录返回 false 而剪掉子树（已由测试验证），
        // 因此改为在每个文件上沿祖先链判断。
        val skippedDirCache = HashMap<VirtualFile, Boolean>()

        /** 判断文件是否位于需跳过的目录下；只检查到所属内容根为止，避免误判项目外的同名目录。 */
        fun isUnderSkippedDirectory(file: VirtualFile): Boolean {
            val contentRoot = fileIndex.getContentRootForFile(file)
            val visited = ArrayList<VirtualFile>()
            var dir = file.parent
            var result = false
            while (dir != null) {
                val cached = skippedDirCache[dir]
                if (cached != null) {
                    result = cached
                    break
                }
                visited.add(dir)
                if (isSkippedDirectory(dir)) {
                    result = true
                    break
                }
                if (dir == contentRoot) break
                dir = dir.parent
            }
            // 结论对路径上所有未缓存的目录都成立：位于被跳过目录之下即被跳过，否则直到内容根都未命中。
            for (visitedDir in visited) skippedDirCache[visitedDir] = result
            return result
        }

        /** 内容迭代回调：只处理普通文件，返回 true 表示继续遍历。 */
        fun visit(file: VirtualFile, explicit: Boolean): Boolean {
            ProgressManager.checkCanceled()
            if (file.isDirectory || !file.isValid || file in accepted || file in oversized) return true
            if (registry.forExtension(file.extension) == null || file.fileType.isBinary) return true
            if (!explicit && (isUnderSkippedDirectory(file) || GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, project))) {
                return true
            }
            if (file.length > MAX_FILE_BYTES) {
                oversized.add(file)
                return true
            }
            accepted[file] = CollectedFile(file, displayPath(file, baseDir))
            return true
        }

        if (roots == null) {
            fileIndex.iterateContent { visit(it, explicit = false) }
        } else {
            for (root in roots) {
                if (!root.isValid) continue
                if (root.isDirectory) {
                    fileIndex.iterateContentUnderDirectory(root) { visit(it, explicit = false) }
                } else {
                    visit(root, explicit = true)
                }
            }
        }
        return CollectedFiles(accepted.values.toList(), oversized.size)
    }

    /** 判断目录是否属于应整体跳过的 IDE 配置目录或 Gradle 构建输出目录。 */
    private fun isSkippedDirectory(dir: VirtualFile): Boolean {
        val name = dir.name
        if (name in ALWAYS_SKIPPED_DIRS) return true
        if (name !in GRADLE_OUTPUT_DIRS) return false
        val parent = dir.parent ?: return false
        return GRADLE_SCRIPTS.any { parent.findChild(it) != null }
    }

    /** 计算展示路径：优先相对项目根目录，否则使用平台展示 URL。 */
    private fun displayPath(file: VirtualFile, baseDir: VirtualFile?): String =
        baseDir?.let { VfsUtilCore.getRelativePath(file, it, '/') } ?: file.presentableUrl

    /** 收集规则常量。 */
    companion object {
        /** 单文件大小上限（字节）。超过该值的文件通常是生成物或数据文件，跳过以控制内存与耗时。 */
        const val MAX_FILE_BYTES: Long = 5L * 1024 * 1024

        /** 任何位置都跳过的目录名。 */
        private val ALWAYS_SKIPPED_DIRS = setOf(".gradle", ".idea", ".git")

        /** 仅当位于 Gradle 模块目录下时才跳过的构建输出目录名。 */
        private val GRADLE_OUTPUT_DIRS = setOf("build", ".cxx", ".externalNativeBuild")

        /** 用于识别 Gradle 模块目录的脚本文件名。 */
        private val GRADLE_SCRIPTS = listOf("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts")
    }
}
