package com.github.yyhh73144max.codetally.toolWindow

import com.github.yyhh73144max.codetally.CodeTallyBundle
import com.github.yyhh73144max.codetally.application.CodeTallyService
import com.github.yyhh73144max.codetally.application.TallyScope
import com.github.yyhh73144max.codetally.application.TallyState
import com.github.yyhh73144max.codetally.domain.model.LanguageKind
import com.github.yyhh73144max.codetally.domain.model.TallyReport
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.JBSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.RowFilter
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableRowSorter

/**
 * 代码统计工具窗口的主面板：工具栏 + 语言过滤 + 汇总表 + 明细表 + 状态栏。
 *
 * 只负责展示与把用户意图转发给 [CodeTallyService]；不做任何文件 I/O 或统计计算。
 * 面板在 EDT 上订阅服务状态，[dispose] 时取消订阅协程，生命周期由所在 Content 管理。
 *
 * @param project 当前项目。
 */
class TallyPanel(private val project: Project) : SimpleToolWindowPanel(true, true), Disposable {

    /** 状态来源。 */
    private val service = project.service<CodeTallyService>()

    /** UI 协程作用域，在 EDT 上运行，面板销毁时取消。 */
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)

    /** 汇总表模型。 */
    private val summaryModel = SummaryTableModel()

    /** 明细表模型。 */
    private val detailModel = DetailTableModel()

    /** 汇总表（不排序，保证“全部”行固定在末尾）。 */
    private val summaryTable = JBTable(summaryModel)

    /** 明细表。 */
    private val detailTable = JBTable(detailModel)

    /** 明细表排序与过滤器。 */
    private val detailSorter = TableRowSorter(detailModel)

    /** 语言过滤下拉框；null 表示全部语言。 */
    private val languageFilter = ComboBox<LanguageKind?>(arrayOf<LanguageKind?>(null, *LanguageKind.entries.toTypedArray()))

    /** 状态说明文本。 */
    private val statusLabel = JBLabel()

    init {
        setupTables()
        setupFilter()
        toolbar = createTopBar()
        setContent(createContent())
        uiScope.launch { service.state.collect { render(it) } }
    }

    /** 配置表格：数字列右对齐、函数数为 null 时显示“—”，明细表支持排序与双击打开文件。 */
    private fun setupTables() {
        val countRenderer = CountCellRenderer()
        for (table in listOf(summaryTable, detailTable)) {
            table.setDefaultRenderer(Int::class.javaObjectType, countRenderer)
            table.setShowGrid(false)
            table.autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        }
        detailTable.rowSorter = detailSorter
        detailTable.columnModel.getColumn(0).preferredWidth = JBUI.scale(360)
        object : DoubleClickListener() {
            /** 双击明细行时在编辑器中打开对应文件。 */
            override fun onDoubleClick(event: MouseEvent): Boolean = openSelectedFile()
        }.installOn(detailTable)
    }

    /** 配置语言过滤下拉框：选择变化时更新明细表的行过滤器。 */
    private fun setupFilter() {
        val allText = CodeTallyBundle.message("filter.all")
        languageFilter.renderer = textListCellRenderer(allText) { it.displayName }
        languageFilter.addActionListener { applyFilter() }
    }

    /** 根据过滤下拉框的选择设置明细表行过滤器。 */
    private fun applyFilter() {
        val selected = languageFilter.selectedItem as? LanguageKind
        detailSorter.rowFilter = selected?.let { language ->
            object : RowFilter<DetailTableModel, Int>() {
                /** 只保留所选语言的文件。 */
                override fun include(entry: Entry<out DetailTableModel, out Int>): Boolean =
                    entry.model.fileAt(entry.identifier).language == language
            }
        }
    }

    /** 创建顶部栏：统计动作按钮 + 语言过滤。 */
    private fun createTopBar(): JPanel {
        val group = DefaultActionGroup().apply {
            ActionManager.getInstance().getAction(TALLY_PROJECT_ACTION_ID)?.let { add(it) }
        }
        val actionToolbar = ActionManager.getInstance().createActionToolbar(TOOLBAR_PLACE, group, true)
        actionToolbar.targetComponent = this
        return JPanel(BorderLayout()).apply {
            add(actionToolbar.component, BorderLayout.WEST)
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
                    add(JBLabel(CodeTallyBundle.message("filter.label")))
                    add(languageFilter)
                },
                BorderLayout.CENTER,
            )
        }
    }

    /** 创建主体：上下分割的汇总表与明细表，底部为状态与函数口径说明。 */
    private fun createContent(): JPanel {
        val splitter = JBSplitter(true, 0.3f).apply {
            firstComponent = ScrollPaneFactory.createScrollPane(summaryTable)
            secondComponent = ScrollPaneFactory.createScrollPane(detailTable)
        }
        val hint = JBLabel(CodeTallyBundle.message("hint.functions")).apply {
            foreground = UIUtil.getContextHelpForeground()
            setAllowAutoWrapping(true)
        }
        val footer = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4, 8)
            add(statusLabel, BorderLayout.NORTH)
            add(hint, BorderLayout.SOUTH)
        }
        return JPanel(BorderLayout()).apply {
            add(splitter, BorderLayout.CENTER)
            add(footer, BorderLayout.SOUTH)
        }
    }

    /**
     * 渲染服务状态。运行中保留上一次结果，避免表格闪烁；完成时整体替换数据。
     *
     * @param state 最新状态。
     */
    private fun render(state: TallyState) {
        statusLabel.text = when (state) {
            TallyState.Idle -> CodeTallyBundle.message("state.idle")
            is TallyState.Running -> CodeTallyBundle.message("state.running", describe(state.scope))
            is TallyState.Done -> {
                showReport(state.report)
                describeDone(state.scope, state.report)
            }
            is TallyState.Cancelled -> CodeTallyBundle.message("state.cancelled", describe(state.scope))
            is TallyState.Failed -> CodeTallyBundle.message("state.failed", describe(state.scope), state.message)
        }
    }

    /** 把报告写入两个表格模型。 */
    private fun showReport(report: TallyReport) {
        summaryModel.update(report.summaries, report.overall.takeIf { report.files.isNotEmpty() })
        detailModel.update(report.files)
    }

    /** 生成完成状态文案，包含文件数与跳过原因。 */
    private fun describeDone(scope: TallyScope, report: TallyReport): String {
        val scopeText = describe(scope)
        if (report.files.isEmpty() && report.skipped.isEmpty()) return CodeTallyBundle.message("state.empty", scopeText)
        val base = CodeTallyBundle.message("state.done", scopeText, report.files.size)
        if (report.skipped.isEmpty()) return base
        val reasons = report.skipped.entries.joinToString("、") { (reason, count) ->
            "${CodeTallyBundle.message("skip.${reason.name}")} $count"
        }
        return base + CodeTallyBundle.message("state.done.skipped", report.skipped.values.sum(), reasons)
    }

    /** 统计范围的简短描述。 */
    private fun describe(scope: TallyScope): String = when (scope) {
        TallyScope.WholeProject -> CodeTallyBundle.message("scope.project")
        is TallyScope.Selection -> if (scope.roots.size == 1) {
            CodeTallyBundle.message("scope.selection.single", scope.roots.first().name)
        } else {
            CodeTallyBundle.message("scope.selection.multiple", scope.roots.first().name, scope.roots.size)
        }
    }

    /**
     * 打开明细表当前选中行对应的文件。
     *
     * @return 是否成功打开；文件已被删除或无选中行时返回 false。
     */
    private fun openSelectedFile(): Boolean {
        val viewRow = detailTable.selectedRow.takeIf { it >= 0 } ?: return false
        val stats = detailModel.fileAt(detailTable.convertRowIndexToModel(viewRow))
        val file = VirtualFileManager.getInstance().findFileByUrl(stats.fileUrl) ?: return false
        OpenFileDescriptor(project, file).navigate(true)
        return true
    }

    /** 面板销毁时取消状态订阅，避免泄漏对已关闭工具窗口的引用。 */
    override fun dispose() {
        uiScope.cancel()
    }

    /** 数字列渲染器：右对齐，null 显示为“—”表示不适用。 */
    private class CountCellRenderer : DefaultTableCellRenderer() {
        init {
            horizontalAlignment = SwingConstants.RIGHT
        }

        /** 把 null 转换为“—”，其余数值原样显示。 */
        override fun setValue(value: Any?) {
            text = value?.toString() ?: "—"
        }
    }

    /** 面板常量。 */
    private companion object {
        /** 工具栏中“统计整个项目”动作的 ID，与 plugin.xml 一致。 */
        const val TALLY_PROJECT_ACTION_ID = "CodeTally.TallyProject"

        /** 工具栏的 ActionPlace。 */
        const val TOOLBAR_PLACE = "CodeTally.ToolWindow"
    }
}
