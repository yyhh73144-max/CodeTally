package com.github.yyhh73144max.codetally.domain.analyzer

/**
 * 词法扫描产出的记号类别。扫描器只保留函数识别所需的最小信息，注释已被剔除，字符串内容被折叠。
 */
internal enum class TokenType {
    /** 标识符或关键字（Kotlin 反引号名称也归入此类）。 */
    IDENT,

    /** 数字字面量。 */
    NUMBER,

    /** 字符串或字符字面量，[Token.text] 只保存起始引号，不保存内容。 */
    STRING,

    /** 单个标点或运算符字符，例如 `(`、`{`、`;`、`:`。 */
    PUNCT,
}

/**
 * 去除注释后的代码记号。
 *
 * @property type 记号类别。
 * @property text 记号文本；标点为单字符，字符串只保留起始引号。
 * @property line 记号所在的 0 基行号，供依赖换行的启发式规则使用。
 */
internal class Token(val type: TokenType, val text: String, val line: Int) {
    /** 判断本记号是否为指定文本的标点。 */
    fun isPunct(value: String): Boolean = type == TokenType.PUNCT && text == value

    /** 判断本记号是否为指定文本的标识符/关键字。 */
    fun isIdent(value: String): Boolean = type == TokenType.IDENT && text == value

    /** 便于调试与测试失败信息阅读的文本形式。 */
    override fun toString(): String = "$type($text)@$line"
}

/**
 * 预先计算成对括号的匹配位置，使分析器可以 O(1) 跳过整段参数列表或代码块。
 *
 * @param tokens 记号序列。
 * @param open 开括号文本，例如 `(`。
 * @param close 闭括号文本，例如 `)`。
 * @return 与 [tokens] 等长的数组：开括号与闭括号位置互相指向对方；未配对的括号与其他记号为 -1。
 */
internal fun matchBrackets(tokens: List<Token>, open: String, close: String): IntArray {
    val result = IntArray(tokens.size) { -1 }
    val stack = ArrayDeque<Int>()
    for ((index, token) in tokens.withIndex()) {
        if (token.type != TokenType.PUNCT) continue
        when (token.text) {
            open -> stack.addLast(index)
            close -> if (stack.isNotEmpty()) {
                val openIndex = stack.removeLast()
                result[openIndex] = index
                result[index] = openIndex
            }
        }
    }
    return result
}
