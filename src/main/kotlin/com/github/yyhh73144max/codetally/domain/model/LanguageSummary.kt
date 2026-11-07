package com.github.yyhh73144max.codetally.domain.model

/**
 * 一组文件的汇总统计（某一语言，或全部语言合计）。
 *
 * @property language 汇总所属语言；为 null 表示“全部语言”合计行。
 * @property fileCount 文件数。
 * @property totalLines 总行数。
 * @property codeLines 代码行数。
 * @property commentLines 注释行数。
 * @property blankLines 空行数。
 * @property functionCount 函数数合计；当参与汇总的文件都不支持函数统计时为 null。
 */
data class LanguageSummary(
    val language: LanguageKind?,
    val fileCount: Int,
    val totalLines: Int,
    val codeLines: Int,
    val commentLines: Int,
    val blankLines: Int,
    val functionCount: Int?,
) {
    /** 汇总构建工具，放在伴生对象中以保持数据类本身不可变。 */
    companion object {
        /**
         * 把若干文件的统计累加为一条汇总。
         *
         * 函数数只累加支持函数统计的文件；若没有任何文件支持函数统计则结果为 null，
         * 以便 UI 区分“0 个函数”和“不适用”。
         *
         * @param language 汇总所属语言，null 表示全部合计。
         * @param files 参与汇总的文件统计。
         * @return 汇总结果；[files] 为空时各项为 0（函数数按语言能力给出 0 或 null）。
         */
        fun of(language: LanguageKind?, files: Collection<FileStats>): LanguageSummary {
            var total = 0
            var code = 0
            var comment = 0
            var blank = 0
            var functions = 0
            var anyFunctionSupport = language?.supportsFunctions ?: false
            for (file in files) {
                val counts = file.counts
                total += counts.totalLines
                code += counts.codeLines
                comment += counts.commentLines
                blank += counts.blankLines
                counts.functionCount?.let {
                    functions += it
                    anyFunctionSupport = true
                }
            }
            return LanguageSummary(
                language = language,
                fileCount = files.size,
                totalLines = total,
                codeLines = code,
                commentLines = comment,
                blankLines = blank,
                functionCount = if (anyFunctionSupport) functions else null,
            )
        }
    }
}
