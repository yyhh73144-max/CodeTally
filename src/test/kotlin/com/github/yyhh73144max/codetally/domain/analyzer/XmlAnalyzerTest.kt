package com.github.yyhh73144max.codetally.domain.analyzer

import com.github.yyhh73144max.codetally.domain.model.LineFunctionCounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [XmlAnalyzer] 的行分类测试（纯 JUnit，不启动 IDE）。
 */
class XmlAnalyzerTest {
    /** 被测分析器。 */
    private val analyzer = XmlAnalyzer()

    /** 验证跨行注释、行尾注释、空行与标签行的分类，且函数数恒为“不适用”（null）。 */
    @Test
    fun classifiesLayoutFile() {
        val source = """
            |<?xml version="1.0" encoding="utf-8"?>
            |<!-- 布局说明
            |     第二行 -->
            |<LinearLayout android:id="@+id/root"> <!-- 行尾注释 -->
            |
            |    <TextView android:text="hi" />
            |</LinearLayout>
        """.trimMargin()
        assertEquals(LineFunctionCounts(7, 4, 2, 1, null), analyzer.analyze(source))
    }

    /** 验证 CDATA 内的 `<!--` 属于数据而不是注释开始，避免后续内容被误判为注释。 */
    @Test
    fun cdataIsCodeAndHidesCommentMarkers() {
        val source = "<a><![CDATA[\n<!-- not comment\n]]></a>\n<b/>"
        val result = analyzer.analyze(source)
        assertEquals(4, result.codeLines)
        assertEquals(0, result.commentLines)
        assertNull(result.functionCount)
    }

    /** 验证未闭合的注释延续到文件末尾而不抛异常。 */
    @Test
    fun unterminatedCommentRunsToEnd() {
        assertEquals(LineFunctionCounts(3, 1, 2, 0, null), analyzer.analyze("<a/>\n<!-- open\nstill"))
    }
}
