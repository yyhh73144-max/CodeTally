package com.github.yyhh73144max.codetally.settings

import com.github.yyhh73144max.codetally.CodeTallyBundle
import com.github.yyhh73144max.codetally.data.CodeTallyProjectSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 设置 › 工具 › CodeTally：编辑项目级自定义排除文件夹。
 *
 * 只负责展示与编辑；持久化与路径换算由 [CodeTallyProjectSettings] 负责。编辑在“应用”前只作用于本页的列表模型。
 * 所有方法由平台在 EDT 调用。
 *
 * @param project 当前项目。
 */
class CodeTallyConfigurable(private val project: Project) : Configurable {

    /** 设置服务。 */
    private val settings = project.service<CodeTallyProjectSettings>()

    /** 未应用的排除文件夹列表（存储路径）。 */
    private val model = CollectionListModel<String>()

    /** 设置页显示名称。 */
    override fun getDisplayName(): String = CodeTallyBundle.message("settings.displayName")

    /** 创建设置页：说明文字 + 带添加/删除按钮的文件夹列表。 */
    override fun createComponent(): JComponent {
        val list = JBList(model).apply {
            cellRenderer = textListCellRenderer { describe(it) }
            emptyText.text = CodeTallyBundle.message("settings.excluded.empty")
        }
        val decorated = ToolbarDecorator.createDecorator(list)
            .setAddAction { addFolders() }
            .disableUpDownActions()
            .createPanel()
        val title = JBLabel(CodeTallyBundle.message("settings.excluded.title"))
        val hint = JBLabel(CodeTallyBundle.message("settings.excluded.hint")).apply {
            foreground = UIUtil.getContextHelpForeground()
            setAllowAutoWrapping(true)
        }
        return JPanel(BorderLayout(0, JBUI.scale(6))).apply {
            add(title, BorderLayout.NORTH)
            add(decorated, BorderLayout.CENTER)
            add(hint, BorderLayout.SOUTH)
        }
    }

    /** 打开多选文件夹对话框（默认定位到项目根目录），把未在列表中的文件夹追加到模型。 */
    private fun addFolders() {
        val descriptor = FileChooserDescriptorFactory.createMultipleFoldersDescriptor()
            .withTitle(CodeTallyBundle.message("settings.excluded.choose"))
        val chosen = FileChooser.chooseFiles(descriptor, project, project.guessProjectDir())
        for (dir in chosen) {
            val stored = settings.toStoredPath(dir)
            if (stored !in model.items) model.add(stored)
        }
    }

    /** 列表项显示文本：项目根目录显示为说明文字，不存在的文件夹追加“不存在”标记。 */
    private fun describe(stored: String): String {
        val text = if (stored.isEmpty()) CodeTallyBundle.message("settings.excluded.root") else stored
        val exists = settings.resolve(stored)?.isDirectory == true
        return if (exists) text else CodeTallyBundle.message("settings.excluded.missing", text)
    }

    /** 列表与已保存设置不同即为已修改。 */
    override fun isModified(): Boolean = model.items != settings.excludedFolders

    /** 保存列表到项目设置；下次统计生效。 */
    override fun apply() {
        settings.setExcludedFolders(model.items)
    }

    /** 从项目设置重新载入列表。 */
    override fun reset() {
        model.replaceAll(settings.excludedFolders)
    }
}
