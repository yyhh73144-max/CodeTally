package com.github.yyhh73144max.codetally.actions

import com.github.yyhh73144max.codetally.application.CodeTallyService
import com.github.yyhh73144max.codetally.application.TallyScope
import com.github.yyhh73144max.codetally.toolWindow.CodeTallyToolWindowFactory
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware

/**
 * “统计整个项目代码”动作（Tools 菜单与工具窗口工具栏）。只把意图转发给 [CodeTallyService] 并显示工具窗口。
 */
class TallyProjectAction : AnAction(), DumbAware {

    /** 只读取 Project，可在后台线程更新。 */
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    /** 有打开的项目时可用。 */
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    /** 启动整个项目的统计（会取消正在进行的统计）并激活工具窗口。 */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.service<CodeTallyService>().startTally(TallyScope.WholeProject)
        CodeTallyToolWindowFactory.show(project)
    }
}
