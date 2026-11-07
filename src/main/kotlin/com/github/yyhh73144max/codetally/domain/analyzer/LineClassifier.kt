package com.github.yyhh73144max.codetally.domain.analyzer

/**
 * 按行累计“代码 / 注释 / 空行”分类的计数器，供各词法扫描器共享。
 *
 * 扫描器逐字符推进：遇到非空白的代码字符调用 [markCode]，遇到非空白的注释字符调用 [markComment]，
 * 遇到任意非换行字符调用 [markContent]，遇到换行（`\n`、`\r\n`、单独的 `\r` 均视为一次）调用 [endLine]。
 * 最后调用 [finish] 得到计数。实例有状态且非线程安全，只能在单次扫描内使用。
 */
internal class LineClassifier {
    /** 已完成的代码行数。 */
    private var code = 0

    /** 已完成的纯注释行数。 */
    private var comment = 0

    /** 已完成的空行数。 */
    private var blank = 0

    /** 当前行是否出现过代码字符。 */
    private var lineHasCode = false

    /** 当前行是否出现过注释字符。 */
    private var lineHasComment = false

    /** 当前行是否出现过任意非换行字符，用于判断文末是否存在未以换行结束的最后一行。 */
    private var lineStarted = false

    /** 标记当前行出现了非空白代码字符；同时视为行内容已开始。 */
    fun markCode() {
        lineHasCode = true
        lineStarted = true
    }

    /** 标记当前行出现了非空白注释字符；同时视为行内容已开始。 */
    fun markComment() {
        lineHasComment = true
        lineStarted = true
    }

    /** 标记当前行出现了字符（可以是空白），使文末无换行的最后一行也被计入总行数。 */
    fun markContent() {
        lineStarted = true
    }

    /** 当前行是否已有代码字符，供扫描器判断 `#` 是否位于行首等场景。 */
    fun currentLineHasCode(): Boolean = lineHasCode

    /** 结束当前行：按“代码优先、其次注释、否则空行”的口径计数，并重置行状态。 */
    fun endLine() {
        when {
            lineHasCode -> code++
            lineHasComment -> comment++
            else -> blank++
        }
        lineHasCode = false
        lineHasComment = false
        lineStarted = false
    }

    /**
     * 结束扫描并返回计数。
     *
     * 若最后一行没有以换行结束且含有字符，则把它计为一行；末尾换行之后的空串不计数。
     *
     * @return 依次为代码行、注释行、空行。
     */
    fun finish(): Triple<Int, Int, Int> {
        if (lineStarted) endLine()
        return Triple(code, comment, blank)
    }
}
