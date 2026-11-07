package com.github.yyhh73144max.codetally.toolWindow

import com.github.yyhh73144max.codetally.CodeTallyBundle
import com.github.yyhh73144max.codetally.domain.model.FileStats
import com.github.yyhh73144max.codetally.domain.model.LanguageSummary
import javax.swing.table.AbstractTableModel

/**
 * 按语言汇总表的模型：每种语言一行，最后一行为“全部”合计。只做领域数据到表格单元的映射。
 *
 * 线程：仅在 EDT 访问。
 */
class SummaryTableModel : AbstractTableModel() {
    /** 当前展示的行（语言汇总 + 合计）。 */
    private var rows: List<LanguageSummary> = emptyList()

    /** 列标题。 */
    private val columns = listOf(
        "column.language", "column.files", "column.total", "column.code",
        "column.comment", "column.blank", "column.functions",
    ).map { CodeTallyBundle.message(it) }

    /**
     * 替换表格数据并通知视图刷新。
     *
     * @param summaries 各语言汇总。
     * @param overall 全部合计；为 null 时（无结果）不显示合计行。
     */
    fun update(summaries: List<LanguageSummary>, overall: LanguageSummary?) {
        rows = if (overall == null) summaries else summaries + overall
        fireTableDataChanged()
    }

    /** 行数。 */
    override fun getRowCount(): Int = rows.size

    /** 列数。 */
    override fun getColumnCount(): Int = columns.size

    /** 列标题。 */
    override fun getColumnName(column: Int): String = columns[column]

    /** 第 0 列为文本，其余为整数（函数数可能为 null，表示不适用）。 */
    override fun getColumnClass(columnIndex: Int): Class<*> =
        if (columnIndex == 0) String::class.java else Int::class.javaObjectType

    /** 按列映射汇总字段；语言为 null 的行显示“全部”。 */
    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        val row = rows[rowIndex]
        return when (columnIndex) {
            0 -> row.language?.displayName ?: CodeTallyBundle.message("summary.all")
            1 -> row.fileCount
            2 -> row.totalLines
            3 -> row.codeLines
            4 -> row.commentLines
            5 -> row.blankLines
            else -> row.functionCount
        }
    }
}

/**
 * 文件明细表的模型：每个文件一行，支持排序与按语言过滤（由视图的 RowSorter 完成）。
 *
 * 线程：仅在 EDT 访问。
 */
class DetailTableModel : AbstractTableModel() {
    /** 当前展示的文件统计。 */
    private var rows: List<FileStats> = emptyList()

    /** 列标题。 */
    private val columns = listOf(
        "column.path", "column.language", "column.total", "column.code",
        "column.comment", "column.blank", "column.functions",
    ).map { CodeTallyBundle.message(it) }

    /**
     * 替换表格数据并通知视图刷新。
     *
     * @param files 文件统计明细。
     */
    fun update(files: List<FileStats>) {
        rows = files
        fireTableDataChanged()
    }

    /**
     * 按模型行号取文件统计，供过滤与双击打开文件使用。
     *
     * @param modelRow 模型行号（不是视图行号）。
     */
    fun fileAt(modelRow: Int): FileStats = rows[modelRow]

    /** 行数。 */
    override fun getRowCount(): Int = rows.size

    /** 列数。 */
    override fun getColumnCount(): Int = columns.size

    /** 列标题。 */
    override fun getColumnName(column: Int): String = columns[column]

    /** 前两列为文本，其余为整数（函数数可能为 null，表示不适用）。 */
    override fun getColumnClass(columnIndex: Int): Class<*> =
        if (columnIndex <= 1) String::class.java else Int::class.javaObjectType

    /** 按列映射文件统计字段。 */
    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        val row = rows[rowIndex]
        val counts = row.counts
        return when (columnIndex) {
            0 -> row.displayPath
            1 -> row.language.displayName
            2 -> counts.totalLines
            3 -> counts.codeLines
            4 -> counts.commentLines
            5 -> counts.blankLines
            else -> counts.functionCount
        }
    }
}
