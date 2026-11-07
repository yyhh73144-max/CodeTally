package com.github.yyhh73144max.codetally.domain.analyzer

import com.github.yyhh73144max.codetally.domain.model.LanguageKind
import com.github.yyhh73144max.codetally.domain.model.LineFunctionCounts

/**
 * Kotlin（`.kt` / `.kts`）分析器。
 *
 * 函数识别规则（词法启发式）：
 * - `fun` 是硬关键字，后面紧跟名称、接收者类型、泛型 `<` 或匿名函数的 `(` 时计为一个函数，
 *   覆盖顶层、成员、扩展、局部、抽象/接口函数与匿名函数；`fun interface` 是类型声明，不计入；
 * - 次构造函数 `constructor(...)` 计入；主构造函数（位于类头 `class A @Inject constructor(...)`）不计入，
 *   通过“去掉修饰符与注解后，前一记号位于更早的行或为 `{` `}` `;`”区分两者；
 * - lambda 不计入。
 *
 * 已知偏差：类名与 `constructor` 被换行分隔的主构造函数会被计为次构造函数。
 */
class KotlinAnalyzer : LanguageAnalyzer {
    /** 固定为 Kotlin。 */
    override val language: LanguageKind = LanguageKind.KOTLIN

    /** Kotlin 词法：可嵌套块注释、三引号原始串、字符串模板、反引号标识符。 */
    private val scanner = CStyleScanner(
        CStyleSyntax(
            nestedBlockComments = true,
            tripleQuoteStrings = true,
            tripleQuoteEscapes = false,
            stringTemplates = true,
            backtickIdentifiers = true,
        ),
    )

    /** 扫描文本后按上述规则统计 `fun` 声明与次构造函数。 */
    override fun analyze(text: CharSequence): LineFunctionCounts {
        val scan = scanner.scan(text)
        return LineFunctionCounts(
            totalLines = scan.totalLines,
            codeLines = scan.codeLines,
            commentLines = scan.commentLines,
            blankLines = scan.blankLines,
            functionCount = countFunctions(scan.tokens),
        )
    }

    /**
     * 统计函数声明数。
     *
     * @param tokens 去除注释后的记号序列。
     * @return 函数与次构造函数数量之和。
     */
    private fun countFunctions(tokens: List<Token>): Int {
        val parens = matchBrackets(tokens, "(", ")")
        var count = 0
        for ((index, token) in tokens.withIndex()) {
            if (token.type != TokenType.IDENT) continue
            when (token.text) {
                "fun" -> if (isFunctionDeclaration(tokens, index)) count++
                "constructor" -> if (isSecondaryConstructor(tokens, parens, index)) count++
            }
        }
        return count
    }

    /** 判断 `fun` 之后的记号是否构成函数声明（排除 `fun interface`）。 */
    private fun isFunctionDeclaration(tokens: List<Token>, funIndex: Int): Boolean {
        val next = tokens.getOrNull(funIndex + 1) ?: return false
        return when (next.type) {
            TokenType.IDENT -> next.text != "interface"
            TokenType.PUNCT -> next.text == "(" || next.text == "<"
            else -> false
        }
    }

    /**
     * 判断 `constructor` 是否为次构造函数。
     *
     * 从 `constructor` 向前跳过可见性修饰符与注解（`@Name`、`@Name(...)`、`@a.b.Name`），
     * 若再往前的记号不存在、位于更早的行，或是 `{` `}` `;`，说明它独立成为一条声明，即次构造函数。
     */
    private fun isSecondaryConstructor(tokens: List<Token>, parens: IntArray, index: Int): Boolean {
        if (tokens.getOrNull(index + 1)?.isPunct("(") != true) return false
        val declarationLine = tokens[index].line
        var k = index - 1
        var firstLine = declarationLine
        while (k >= 0) {
            val token = tokens[k]
            when {
                token.type == TokenType.IDENT && token.text in MODIFIERS -> {
                    firstLine = token.line
                    k--
                }
                token.isPunct(")") && parens[k] >= 0 && isAnnotationName(tokens, parens[k] - 1) -> {
                    k = annotationStart(tokens, parens[k] - 1)
                    firstLine = tokens[k].line
                    k--
                }
                token.type == TokenType.IDENT && isAnnotationName(tokens, k) -> {
                    k = annotationStart(tokens, k)
                    firstLine = tokens[k].line
                    k--
                }
                else -> break
            }
        }
        if (k < 0) return true
        val previous = tokens[k]
        return previous.line < firstLine || previous.isPunct("{") || previous.isPunct("}") || previous.isPunct(";")
    }

    /** 判断下标处的标识符是否为注解名称的最后一段（向前经 `.` 链能到达 `@`）。 */
    private fun isAnnotationName(tokens: List<Token>, index: Int): Boolean {
        if (index < 0) return false
        return tokens[annotationStart(tokens, index)].isPunct("@")
    }

    /** 从注解名最后一段向前回溯 `a.b.Name` 链，返回可能的 `@` 位置（不是注解时返回链首）。 */
    private fun annotationStart(tokens: List<Token>, index: Int): Int {
        var k = index
        while (k - 2 >= 0 && tokens[k].type == TokenType.IDENT && tokens[k - 1].isPunct(".") &&
            tokens[k - 2].type == TokenType.IDENT
        ) {
            k -= 2
        }
        return if (k - 1 >= 0 && tokens[k - 1].isPunct("@")) k - 1 else k
    }

    /** Kotlin 分析器常量。 */
    private companion object {
        /** 可出现在次构造函数前的修饰符。 */
        val MODIFIERS = setOf("public", "private", "protected", "internal")
    }
}
