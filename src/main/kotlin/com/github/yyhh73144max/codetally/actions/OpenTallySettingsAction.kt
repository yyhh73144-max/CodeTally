package com.github.yyhh73144max.codetally.actions

import com.github.yyhh73144max.codetally.settings.CodeTallyConfigurable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware

/**
 * 工具窗口工具栏：打开 CodeTally 设置页（编辑项目级排除文件夹）。
 */
class OpenTallySettingsAction : AnAction(), DumbAware {

    /** 只读取 Project，可在后台线程更新。 */
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    /** 有打开的项目时可用。 */
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    /** 打开设置对话框并定位到 CodeTally 设置页。 */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ShowSettingsUtil.getInstance().showSettingsDialog(project, CodeTallyConfigurable::class.java)
    }
}
