package com.github.yyhh73144max.codetally.domain.model

/**
 * 单段文本的行数与函数数统计结果，是词法分析器的输出。
 *
 * 行分类口径：
 * - 空行：只含空白字符的行（即使位于块注释或多行字符串内部）；
 * - 代码行：含有任意有效代码字符的行（代码后带行尾注释也算代码行；多行字符串内的非空行算代码行）；
 * - 注释行：非空、且只含注释内容的行。
 *
 * 因此恒有 `totalLines == codeLines + commentLines + blankLines`。
 *
 * @property totalLines 物理行总数；空文本为 0，末尾换行符之后的空串不计为一行。
 * @property codeLines 代码行数。
 * @property commentLines 纯注释行数。
 * @property blankLines 空行数。
 * @property functionCount 函数数（词法启发式估算）；语言没有函数概念时为 null。
 */
data class LineFunctionCounts(
    val totalLines: Int,
    val codeLines: Int,
    val commentLines: Int,
    val blankLines: Int,
    val functionCount: Int?,
)
