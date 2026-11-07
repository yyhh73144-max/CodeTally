package com.github.yyhh73144max.codetally.actions

import com.github.yyhh73144max.codetally.CodeTallyBundle
import com.github.yyhh73144max.codetally.data.CodeTallyProjectSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware

/**
 * 项目视图右键：把选中目录加入/移出代码统计的项目级排除列表。
 *
 * 选中项全部已排除时显示“取消代码统计排除”，否则显示“在代码统计中排除”；选中项含文件时不显示。
 * 修改在下次统计时生效。
 */
class ToggleExcludeFolderAction : AnAction(), DumbAware {

    /** 只读取选中目录与设置快照，可在后台线程更新。 */
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    /** 仅当选中项全部为目录时可见，并根据当前排除状态切换文案。 */
    override fun update(e: AnActionEvent) {
        val project = e.project
        val dirs = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (project == null || dirs.isNullOrEmpty() || !dirs.all { it.isDirectory }) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        e.presentation.isEnabledAndVisible = true
        val settings = project.service<CodeTallyProjectSettings>()
        e.presentation.text = CodeTallyBundle.message(
            if (dirs.all(settings::isExcluded)) "toggle.include" else "toggle.exclude",
        )
    }

    /** 切换选中目录的排除状态。 */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dirs = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.filter { it.isDirectory }.orEmpty()
        if (dirs.isEmpty()) return
        project.service<CodeTallyProjectSettings>().toggleExcluded(dirs)
    }
}
