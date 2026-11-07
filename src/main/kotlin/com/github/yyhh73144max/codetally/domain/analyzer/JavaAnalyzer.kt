package com.github.yyhh73144max.codetally.domain.analyzer

import com.github.yyhh73144max.codetally.domain.model.LanguageKind
import com.github.yyhh73144max.codetally.domain.model.LineFunctionCounts

/**
 * Java 分析器。
 *
 * 函数识别规则（词法启发式）：维护花括号作用域栈，区分“类型体”（class/interface/enum/record/注解类型
 * 及匿名类体）与“代码块”。只有直接位于类型体中的 `名称 ( ... ) [throws ...] {` 或 `... ;` 才计为方法，
 * 因此方法体内的调用、控制语句、lambda 都不会被计入；构造器、抽象方法、接口方法、注解成员方法、
 * 匿名类与局部类中的方法均计入。枚举常量列表（第一个 `;` 之前）不计入，但带体的枚举常量内的方法计入。
 */
class JavaAnalyzer : LanguageAnalyzer {
    /** 固定为 Java。 */
    override val language: LanguageKind = LanguageKind.JAVA

    /** Java 词法：文本块（支持转义）、标识符可含 `$`。 */
    private val scanner = CStyleScanner(
        CStyleSyntax(
            tripleQuoteStrings = true,
            tripleQuoteEscapes = true,
            dollarInIdentifiers = true,
        ),
    )

    /** 扫描文本后按作用域栈规则统计方法数。 */
    override fun analyze(text: CharSequence): LineFunctionCounts {
        val scan = scanner.scan(text)
        return LineFunctionCounts(
            totalLines = scan.totalLines,
            codeLines = scan.codeLines,
            commentLines = scan.commentLines,
            blankLines = scan.blankLines,
            functionCount = countMethods(scan.tokens),
        )
    }

    /** 花括号作用域类别。 */
    private enum class Scope {
        /** 类型体：其中的 `名称(` 可能是方法声明。 */
        TYPE,

        /** 枚举体中第一个 `;` 之前的常量列表区域。 */
        ENUM_CONSTANTS,

        /** 方法体、初始化块、lambda、数组初始化等代码块。 */
        CODE,
    }

    /**
     * 统计方法数。
     *
     * @param tokens 去除注释后的记号序列。
     * @return 方法（含构造器）数量。
     */
    private fun countMethods(tokens: List<Token>): Int {
        val parens = matchBrackets(tokens, "(", ")")
        val scopes = ArrayDeque<Scope>()
        // 已识别为方法体的 `{` 位置，遇到时压入 CODE 而不是再做类型判断。
        val methodBodyBraces = HashSet<Int>()
        var pendingType: Scope? = null
        var count = 0
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            val previous = tokens.getOrNull(index - 1)
            if (token.type == TokenType.IDENT) {
                pendingType = typeKeywordScope(tokens, index, previous) ?: pendingType
            } else if (token.type == TokenType.PUNCT) {
                when (token.text) {
                    "(" -> {
                        val close = parens[index]
                        if (scopes.lastOrNull() == Scope.TYPE && isMethodName(tokens, index - 1) && close >= 0) {
                            val end = methodHeaderEnd(tokens, close + 1)
                            if (end >= 0) {
                                count++
                                if (tokens[end].isPunct("{")) methodBodyBraces.add(end)
                            }
                        }
                        // 参数列表中不会出现方法声明，直接跳过整段以减少误判。
                        if (close > index) index = close
                    }
                    "{" -> {
                        val scope = when {
                            index in methodBodyBraces -> Scope.CODE
                            pendingType != null -> pendingType
                            previous != null && previous.isPunct(")") && isAnonymousClass(tokens, parens[index - 1]) ->
                                Scope.TYPE
                            // 带体的枚举常量 `A { ... }` / `A(1) { ... }` 是匿名类体。
                            scopes.lastOrNull() == Scope.ENUM_CONSTANTS -> Scope.TYPE
                            else -> Scope.CODE
                        }
                        scopes.addLast(scope)
                        pendingType = null
                    }
                    "}" -> scopes.removeLastOrNull()
                    ";" -> {
                        if (scopes.lastOrNull() == Scope.ENUM_CONSTANTS) {
                            scopes.removeLast()
                            scopes.addLast(Scope.TYPE)
                        }
                        pendingType = null
                    }
                }
            }
            index++
        }
        return count
    }

    /**
     * 若下标处是类型声明关键字，返回其将要打开的作用域类别。
     *
     * `Foo.class` 中的 `class` 前有 `.`，不是声明；`record` 是上下文关键字，仅在 `record 名称 (` 形式下生效；
     * `@interface` 中的 `interface` 按普通类型处理。
     */
    private fun typeKeywordScope(tokens: List<Token>, index: Int, previous: Token?): Scope? {
        if (previous?.isPunct(".") == true) return null
        return when (tokens[index].text) {
            "class", "interface" -> Scope.TYPE
            "enum" -> Scope.ENUM_CONSTANTS
            "record" -> if (tokens.getOrNull(index + 1)?.type == TokenType.IDENT &&
                tokens.getOrNull(index + 2)?.isPunct("(") == true
            ) {
                Scope.TYPE
            } else {
                null
            }
            else -> null
        }
    }

    /**
     * 判断 `(` 前的记号是否可以是方法名：必须是非关键字标识符，且前一个记号不是 `@`（注解）、
     * `.`（限定调用）、`new`（实例化）、`=`/`,`（初始化表达式）或 `record`（记录头）。
     */
    private fun isMethodName(tokens: List<Token>, index: Int): Boolean {
        val name = tokens.getOrNull(index) ?: return false
        if (name.type != TokenType.IDENT || name.text in NON_METHOD_KEYWORDS) return false
        val before = tokens.getOrNull(index - 1) ?: return true
        if (before.type == TokenType.PUNCT && before.text in NON_METHOD_PRECEDING_PUNCT) return false
        return !(before.type == TokenType.IDENT && (before.text == "new" || before.text == "record"))
    }

    /**
     * 从参数列表闭括号之后寻找方法头的结束位置。
     *
     * 允许旧式数组返回 `[]`、`throws` 子句与注解成员的 `default` 值。
     *
     * @return 方法体 `{` 或声明结束 `;` 的下标；不是方法头（例如 `=`）时返回 -1。
     */
    private fun methodHeaderEnd(tokens: List<Token>, start: Int): Int {
        var k = start
        while (k < tokens.size) {
            val token = tokens[k]
            when {
                token.isPunct("{") || token.isPunct(";") -> return k
                token.isPunct("[") || token.isPunct("]") -> k++
                token.isIdent("throws") -> {
                    k++
                    // throws 子句只含类型名、`.`、`,` 与泛型尖括号。
                    while (k < tokens.size && !tokens[k].isPunct("{") && !tokens[k].isPunct(";") &&
                        (tokens[k].type == TokenType.IDENT || tokens[k].type == TokenType.PUNCT && tokens[k].text in THROWS_PUNCT)
                    ) {
                        k++
                    }
                }
                token.isIdent("default") -> {
                    while (k < tokens.size && !tokens[k].isPunct(";")) k++
                    return if (k < tokens.size) k else -1
                }
                else -> return -1
            }
        }
        return -1
    }

    /**
     * 判断以 [openParen] 开始的参数列表是否属于 `new 类型(...)`，若是则其后的 `{` 为匿名类体。
     *
     * 向前跳过类型名中的标识符、`.` 与泛型尖括号，看是否到达 `new`。
     */
    private fun isAnonymousClass(tokens: List<Token>, openParen: Int): Boolean {
        if (openParen < 1) return false
        var k = openParen - 1
        var angleDepth = 0
        while (k >= 0) {
            val token = tokens[k]
            when {
                token.isIdent("new") -> return angleDepth == 0
                token.isPunct(">") -> angleDepth++
                token.isPunct("<") -> angleDepth--
                token.type == TokenType.IDENT || token.isPunct(".") || token.isPunct(",") || token.isPunct("?") -> Unit
                else -> return false
            }
            k--
        }
        return false
    }

    /** Java 分析器常量。 */
    private companion object {
        /** 后面跟 `(` 但不是方法名的关键字。 */
        val NON_METHOD_KEYWORDS = setOf(
            "if", "for", "while", "switch", "catch", "synchronized", "try", "return", "new", "throw",
            "else", "do", "case", "assert", "super", "this", "yield",
        )

        /** 出现在名称之前时说明它不是方法声明的标点。 */
        val NON_METHOD_PRECEDING_PUNCT = setOf("@", ".", "=", ",", "(", "!", "&", "|", "?", ":", "+", "-", "*", "/")

        /** throws 子句中允许出现的标点。 */
        val THROWS_PUNCT = setOf(".", ",", "<", ">", "?")
    }
}
