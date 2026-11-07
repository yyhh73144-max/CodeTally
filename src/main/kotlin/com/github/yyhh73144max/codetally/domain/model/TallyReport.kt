package com.github.yyhh73144max.codetally.domain.model

/**
 * 一次统计的完整、不可变报告。
 *
 * @property files 每个文件的明细，按展示路径排序。
 * @property summaries 按语言的汇总，只包含至少有一个文件的语言，顺序与 [LanguageKind] 声明顺序一致。
 * @property overall 全部语言合计。
 * @property skipped 各跳过原因对应的文件数，只包含计数大于 0 的原因。
 */
data class TallyReport(
    val files: List<FileStats>,
    val summaries: List<LanguageSummary>,
    val overall: LanguageSummary,
    val skipped: Map<SkipReason, Int>,
) {
    /** 报告构建入口，负责排序与汇总计算，保证报告内各部分口径一致。 */
    companion object {
        /**
         * 由文件明细与跳过计数构建报告。
         *
         * @param files 文件明细，顺序不限。
         * @param skipped 跳过计数；值为 0 的项会被移除。
         * @return 汇总完成的报告。
         */
        fun build(files: Collection<FileStats>, skipped: Map<SkipReason, Int>): TallyReport {
            val sorted = files.sortedBy { it.displayPath }
            val byLanguage = sorted.groupBy { it.language }
            val summaries = LanguageKind.entries
                .mapNotNull { language -> byLanguage[language]?.let { LanguageSummary.of(language, it) } }
            return TallyReport(
                files = sorted,
                summaries = summaries,
                overall = LanguageSummary.of(null, sorted),
                skipped = skipped.filterValues { it > 0 },
            )
        }
    }
}
