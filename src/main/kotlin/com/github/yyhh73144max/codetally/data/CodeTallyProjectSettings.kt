package com.github.yyhh73144max.codetally.data

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * 项目级统计设置：用户自定义的排除文件夹列表。
 *
 * 持久化到 `.idea/codetally.xml`，可随项目提交共享。路径存储格式：
 * - 位于项目根目录下的文件夹存为相对路径（`/` 分隔，项目根目录本身为空串），保证团队成员机器间可移植；
 * - 位于项目根目录外的文件夹存为 VFS URL（如 `file://D:/libs`）。
 * 已删除或不存在的文件夹保留在设置中但在统计时被忽略，避免目录临时缺失时丢失配置。
 *
 * 线程：读写方法以本实例为锁同步，可在 EDT 与后台读操作中调用；解析路径需要访问 VFS，统计时在读操作中调用。
 *
 * @param project 当前项目，用于确定项目根目录。
 */
@Service(Service.Level.PROJECT)
@State(name = "CodeTallySettings", storages = [Storage("codetally.xml")])
class CodeTallyProjectSettings(private val project: Project) :
    SimplePersistentStateComponent<CodeTallyProjectSettings.SettingsState>(SettingsState()) {

    /** 持久化状态。 */
    class SettingsState : BaseState() {
        /** 排除文件夹的存储路径（相对路径或 URL），保持用户添加顺序。 */
        var excludedFolders by list<String>()
    }

    /** 当前排除文件夹存储路径的快照。 */
    @get:Synchronized
    val excludedFolders: List<String>
        get() = state.excludedFolders.toList()

    /**
     * 整体替换排除文件夹列表（设置页“应用”时调用），自动去除首尾空白与末尾 `/` 并去重。
     *
     * @param paths 存储路径列表。
     */
    @Synchronized
    fun setExcludedFolders(paths: Collection<String>) {
        val normalized = paths.map { it.trim().trimEnd('/') }.distinct()
        // 整体赋值新列表：属性委托的 setter 会递增修改计数，平台据此保存设置（无需调用内部 API）。
        state.excludedFolders = normalized.toMutableList()
    }

    /**
     * 判断目录本身是否在排除列表中（不检查祖先）。
     *
     * @param dir 目录。
     */
    fun isExcluded(dir: VirtualFile): Boolean = toStoredPath(dir) in excludedFolders

    /**
     * 切换一组目录的排除状态：若全部已排除则全部移除，否则把未排除的加入列表。
     *
     * @param dirs 项目视图中选中的目录。
     */
    @Synchronized
    fun toggleExcluded(dirs: Collection<VirtualFile>) {
        val paths = dirs.map(::toStoredPath)
        val current = state.excludedFolders.toList()
        val updated = if (paths.all { it in current }) current - paths.toSet() else current + paths.filterNot { it in current }
        setExcludedFolders(updated)
    }

    /**
     * 解析出当前存在的排除目录。
     *
     * @return 存在且为目录的排除文件夹集合。
     */
    fun excludedDirectories(): Set<VirtualFile> =
        excludedFolders.mapNotNullTo(HashSet()) { resolve(it)?.takeIf(VirtualFile::isDirectory) }

    /**
     * 把目录转换为存储路径：项目根目录下为相对路径，否则为 URL。
     *
     * @param dir 目录。
     */
    fun toStoredPath(dir: VirtualFile): String {
        val base = project.guessProjectDir()
        return base?.let { VfsUtilCore.getRelativePath(dir, it, '/') } ?: dir.url
    }

    /**
     * 把存储路径解析为文件。
     *
     * @param stored 存储路径。
     * @return 对应文件；不存在或项目根目录不可用时返回 null。
     */
    fun resolve(stored: String): VirtualFile? {
        if (URL_SEPARATOR in stored) return VirtualFileManager.getInstance().findFileByUrl(stored)
        val base = project.guessProjectDir() ?: return null
        return if (stored.isEmpty()) base else base.findFileByRelativePath(stored)
    }

    /** 设置常量。 */
    private companion object {
        /** 区分 URL 与相对路径的标记。 */
        const val URL_SEPARATOR = "://"
    }
}
