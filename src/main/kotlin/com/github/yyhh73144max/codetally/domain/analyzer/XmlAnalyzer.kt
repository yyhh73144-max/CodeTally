package com.github.yyhh73144max.codetally.domain.analyzer

import com.github.yyhh73144max.codetally.domain.model.LanguageKind
import com.github.yyhh73144max.codetally.domain.model.LineFunctionCounts

/**
 * XML 分析器（Android 布局、资源、清单等）。
 *
 * `<!-- ... -->` 内的非空白内容计为注释；`<![CDATA[ ... ]]>` 内容属于文档数据，计为代码，
 * 其中出现的 `<!--` 不会被误认为注释；其他非空白内容（标签、属性、文本、处理指令）计为代码。
 * XML 没有函数概念，函数数恒为 null。
 */
class XmlAnalyzer : LanguageAnalyzer {
    /** 固定为 XML。 */
    override val language: LanguageKind = LanguageKind.XML

    /** 逐字符扫描并分类行；未闭合的注释或 CDATA 延续到文件末尾。 */
    override fun analyze(text: CharSequence): LineFunctionCounts {
        val lines = LineClassifier()
        var inComment = false
        var inCdata = false
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (c == '\n' || c == '\r') {
                i += if (c == '\r' && i + 1 < n && text[i + 1] == '\n') 2 else 1
                lines.endLine()
                continue
            }
            lines.markContent()
            when {
                inComment -> {
                    if (!c.isWhitespace()) lines.markComment()
                    if (text.startsWith(COMMENT_END, i)) {
                        inComment = false
                        i += COMMENT_END.length
                    } else {
                        i++
                    }
                }
                inCdata -> {
                    if (!c.isWhitespace()) lines.markCode()
                    if (text.startsWith(CDATA_END, i)) {
                        inCdata = false
                        i += CDATA_END.length
                    } else {
                        i++
                    }
                }
                c.isWhitespace() -> i++
                text.startsWith(COMMENT_START, i) -> {
                    lines.markComment()
                    inComment = true
                    i += COMMENT_START.length
                }
                text.startsWith(CDATA_START, i) -> {
                    lines.markCode()
                    inCdata = true
                    i += CDATA_START.length
                }
                else -> {
                    lines.markCode()
                    i++
                }
            }
        }
        val (code, comment, blank) = lines.finish()
        return LineFunctionCounts(
            totalLines = code + comment + blank,
            codeLines = code,
            commentLines = comment,
            blankLines = blank,
            functionCount = null,
        )
    }

    /** XML 分界符常量。 */
    private companion object {
        /** 注释开始。 */
        const val COMMENT_START = "<!--"

        /** 注释结束。 */
        const val COMMENT_END = "-->"

        /** CDATA 开始。 */
        const val CDATA_START = "<![CDATA["

        /** CDATA 结束。 */
        const val CDATA_END = "]]>"
    }
}
