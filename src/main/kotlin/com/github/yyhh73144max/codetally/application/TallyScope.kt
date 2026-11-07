package com.github.yyhh73144max.codetally.application

import com.intellij.openapi.vfs.VirtualFile

/**
 * 一次统计的范围。
 */
sealed interface TallyScope {
    /** 整个项目的内容根。 */
    data object WholeProject : TallyScope

    /**
     * 用户选中的文件或目录。
     *
     * @property roots 选中的文件/目录，至少一个。
     */
    data class Selection(val roots: List<VirtualFile>) : TallyScope {
        init {
            require(roots.isNotEmpty()) { "统计范围至少需要一个文件或目录" }
        }
    }
}
