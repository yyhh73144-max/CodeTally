package com.github.yyhh73144max.codetally.toolWindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

/**
 * “代码统计”工具窗口工厂：只负责装配面板并把面板生命周期绑定到 Content。
 *
 * 实现 [DumbAware]：统计不依赖索引，索引构建期间工具窗口也可用。
 */
class CodeTallyToolWindowFactory : ToolWindowFactory, DumbAware {

    /** 创建 [TallyPanel] 并以其作为 Content 的 disposer，Content 移除或项目关闭时释放面板。 */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = TallyPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }

    /** 工具窗口相关常量与入口。 */
    companion object {
        /** 工具窗口 ID，与 plugin.xml 一致。 */
        const val ID = "CodeTally"

        /**
         * 激活并显示代码统计工具窗口；工具窗口未注册（理论上不会发生）时静默忽略。
         *
         * 必须在 EDT 调用。
         *
         * @param project 当前项目。
         */
        fun show(project: Project) {
            ToolWindowManager.getInstance(project).getToolWindow(ID)?.activate(null)
        }
    }
}
