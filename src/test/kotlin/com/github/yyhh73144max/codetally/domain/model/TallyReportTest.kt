package com.github.yyhh73144max.codetally.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [TallyReport] 汇总口径测试。
 */
class TallyReportTest {

    /** 构造测试用的文件统计。 */
    private fun stats(path: String, language: LanguageKind, total: Int, functions: Int?) =
        FileStats(path, "temp:///$path", language, LineFunctionCounts(total, total - 1, 1, 0, functions))

    /** 验证按语言分组汇总、语言顺序固定、合计正确，以及 XML 的函数数不计入合计且自身为 null。 */
    @Test
    fun aggregatesPerLanguageAndOverall() {
        val report = TallyReport.build(
            listOf(
                stats("b.xml", LanguageKind.XML, 10, null),
                stats("a.kt", LanguageKind.KOTLIN, 20, 3),
                stats("c.kt", LanguageKind.KOTLIN, 5, 1),
            ),
            mapOf(SkipReason.TOO_LARGE to 0, SkipReason.READ_FAILED to 2),
        )
        assertEquals(listOf("a.kt", "b.xml", "c.kt"), report.files.map { it.displayPath })
        assertEquals(listOf(LanguageKind.KOTLIN, LanguageKind.XML), report.summaries.map { it.language })
        val kotlin = report.summaries[0]
        assertEquals(2, kotlin.fileCount)
        assertEquals(25, kotlin.totalLines)
        assertEquals(4, kotlin.functionCount)
        assertNull(report.summaries[1].functionCount)
        assertEquals(3, report.overall.fileCount)
        assertEquals(35, report.overall.totalLines)
        assertEquals(4, report.overall.functionCount)
        assertEquals(mapOf(SkipReason.READ_FAILED to 2), report.skipped)
    }
}
