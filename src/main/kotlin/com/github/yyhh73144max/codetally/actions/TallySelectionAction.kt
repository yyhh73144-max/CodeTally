package com.github.yyhh73144max.codetally.actions

import com.github.yyhh73144max.codetally.application.CodeTallyService
import com.github.yyhh73144max.codetally.application.TallyScope
import com.github.yyhh73144max.codetally.toolWindow.CodeTallyToolWindowFactory
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware

/**
 * “统计所选文件代码”动作（项目视图右键菜单）：统计选中的文件与目录（目录递归）。
 */
class TallySelectionAction : AnAction(), DumbAware {

    /** 只读取选中文件数组，可在后台线程更新。 */
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    /** 有项目且至少选中一个文件/目录时可见。 */
    override fun update(e: AnActionEvent) {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        e.presentation.isEnabledAndVisible = e.project != null && !files.isNullOrEmpty()
    }

    /** 以选中项为范围启动统计（会取消正在进行的统计）并激活工具窗口。 */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.toList().orEmpty()
        if (files.isEmpty()) return
        project.service<CodeTallyService>().startTally(TallyScope.Selection(files))
        CodeTallyToolWindowFactory.show(project)
    }
}
