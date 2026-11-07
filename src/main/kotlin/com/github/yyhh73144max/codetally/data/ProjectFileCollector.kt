package com.github.yyhh73144max.codetally.data

import com.github.yyhh73144max.codetally.domain.analyzer.AnalyzerRegistry
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
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
 * - 排除所有隐藏目录（名称以 `.` 开头，如 `.gradle`、`.gradle-user`、`.gradle-tmp`、`.idea`、`.kotlin`、`.cxx`），
 *   这类目录存放 IDE/构建工具/AI 工具的缓存与临时文件，不是项目源码；
 * - 排除 Gradle 模块（同级存在 `build.gradle(.kts)`/`settings.gradle(.kts)`）下的 `build` 输出目录——
 *   只按名称排除会误伤名为 `build` 的源码包，因此要求同级有 Gradle 脚本；
 * - 排除用户在 [CodeTallyProjectSettings] 中配置的项目级自定义文件夹（含其全部子目录）；
 * - 排除任意 `GeneratedSourcesFilter` 认定的生成源码（Android 插件会注册此过滤器）；
 * - 只保留 [AnalyzerRegistry] 支持的扩展名，排除二进制类型与超过大小上限的文件；
 * - 用户显式选中的单个文件只校验扩展名、类型与大小；显式选中的目录本身即使是被跳过的目录（如 `.gradle-user`）
 *   也会统计，但其内部的被跳过子目录仍会排除，尊重用户的明确选择。
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
        // 在本次读操作内解析一次自定义排除目录，统计期间设置变化不影响本次结果。
        val customExcluded = project.service<CodeTallyProjectSettings>().excludedDirectories()
        /**
         * 内容迭代回调：只处理普通文件，返回 true 表示继续遍历。
         *
         * @param skipChecker 目录排除判定器；为 null 表示显式选中的单个文件，不做目录排除。
         */
        fun visit(file: VirtualFile, skipChecker: DirectorySkipChecker?): Boolean {
            ProgressManager.checkCanceled()
            if (file.isDirectory || !file.isValid || file in accepted || file in oversized) return true
            if (registry.forExtension(file.extension) == null || file.fileType.isBinary) return true
            if (skipChecker != null &&
                (skipChecker.isUnderSkippedDirectory(file) || GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, project))
            ) {
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
            val checker = DirectorySkipChecker(fileIndex, stopAt = null, customExcluded)
            fileIndex.iterateContent { visit(it, checker) }
        } else {
            for (root in roots) {
                if (!root.isValid) continue
                if (root.isDirectory) {
                    // 判定只检查到选中目录为止（不含），因此显式选中隐藏目录时仍会统计其内容。
                    val checker = DirectorySkipChecker(fileIndex, stopAt = root, customExcluded)
                    fileIndex.iterateContentUnderDirectory(root) { visit(it, checker) }
                } else {
                    visit(root, skipChecker = null)
                }
            }
        }
        return CollectedFiles(accepted.values.toList(), oversized.size)
    }

    /**
     * 判断文件是否位于需跳过的目录下，并按目录缓存结论。
     *
     * 平台的 `iterateContent(processor, filter)` 不会因 filter 对目录返回 false 而剪掉子树（已由测试验证），
     * 因此在每个文件上沿祖先链判断；同一目录下的大量文件只需判断一次。实例只在一次读操作内使用，非线程安全。
     *
     * 祖先链一直向上检查到离开项目内容为止，会穿过嵌套的内容根（例如 Gradle `includeBuild` 引入的
     * `third_party/dexkit` 是独立内容根，但外层 `third_party` 被排除时它也必须被排除）。
     *
     * @param fileIndex 项目文件索引，用于判断目录是否仍属于项目内容。
     * @param stopAt 祖先链检查的上界（不含）；null 表示检查到离开项目内容为止。
     * @param customExcluded 用户自定义排除的目录。
     */
    private class DirectorySkipChecker(
        private val fileIndex: ProjectFileIndex,
        private val stopAt: VirtualFile?,
        private val customExcluded: Set<VirtualFile>,
    ) {
        /** 目录 -> “该目录或其祖先（至上界）是否被跳过”。 */
        private val cache = HashMap<VirtualFile, Boolean>()

        /** 判断 [file] 的祖先目录中是否存在需跳过的目录；不检查项目内容之外的目录，避免误判外部同名目录。 */
        fun isUnderSkippedDirectory(file: VirtualFile): Boolean {
            val visited = ArrayList<VirtualFile>()
            var dir = file.parent
            var result = false
            // honorExclusion = false：被 IDE 标记为 excluded 的中间目录仍视为项目内容，继续向上检查。
            while (dir != null && dir != stopAt && fileIndex.getContentRootForFile(dir, false) != null) {
                val cached = cache[dir]
                if (cached != null) {
                    result = cached
                    break
                }
                visited.add(dir)
                if (isSkippedDirectory(dir)) {
                    result = true
                    break
                }
                dir = dir.parent
            }
            // 结论对路径上所有未缓存的目录都成立：位于被跳过目录之下即被跳过，否则直到上界都未命中。
            for (visitedDir in visited) cache[visitedDir] = result
            return result
        }

        /** 判断单个目录是否为自定义排除目录、隐藏目录或 Gradle 模块的 build 输出目录。 */
        private fun isSkippedDirectory(dir: VirtualFile): Boolean {
            if (dir in customExcluded) return true
            val name = dir.name
            if (name.startsWith(".")) return true
            if (name != GRADLE_OUTPUT_DIR) return false
            val parent = dir.parent ?: return false
            return GRADLE_SCRIPTS.any { parent.findChild(it) != null }
        }
    }

    /** 计算展示路径：优先相对项目根目录，否则使用平台展示 URL。 */
    private fun displayPath(file: VirtualFile, baseDir: VirtualFile?): String =
        baseDir?.let { VfsUtilCore.getRelativePath(file, it, '/') } ?: file.presentableUrl

    /** 收集规则常量。 */
    companion object {
        /** 单文件大小上限（字节）。超过该值的文件通常是生成物或数据文件，跳过以控制内存与耗时。 */
        const val MAX_FILE_BYTES: Long = 5L * 1024 * 1024

        /** 仅当位于 Gradle 模块目录下时才跳过的构建输出目录名（隐藏目录如 `.cxx` 已被统一跳过）。 */
        private const val GRADLE_OUTPUT_DIR = "build"

        /** 用于识别 Gradle 模块目录的脚本文件名。 */
        private val GRADLE_SCRIPTS = listOf("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts")
    }
}
