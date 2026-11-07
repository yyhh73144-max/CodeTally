package com.github.yyhh73144max.codetally.domain.analyzer

import com.github.yyhh73144max.codetally.domain.model.LanguageKind
import com.github.yyhh73144max.codetally.domain.model.LineFunctionCounts

/**
 * C / C++ 分析器（含头文件，适用于 NDK/JNI 代码）。
 *
 * 函数识别规则（词法启发式）：只统计**函数定义**（带函数体），不统计原型/声明，避免头文件与实现文件重复计数。
 * 维护花括号作用域栈，区分“声明作用域”（文件顶层、namespace、class/struct/union 体、`extern "C" {}`）与“代码块”；
 * 在声明作用域中，`名称 ( ... )` 之后（允许 const/noexcept/override、尾置返回类型、构造函数初始化列表）遇到 `{`
 * 即计为一个函数定义；遇到 `;` 或 `=` 则视为声明、`= default`/`= delete`/纯虚函数，不计入。
 * 预处理指令由扫描器整体剔除，宏定义中的括号不会干扰识别。
 *
 * 已知偏差：用宏生成的函数、`#if` 分支中重复的函数头可能导致少计或多计；
 * 形如 `TEST(Suite, Name) {` 的宏调用会被计为函数（其展开结果本身就是函数定义）。
 */
class CAnalyzer : LanguageAnalyzer {
    /** 固定为 C/C++。 */
    override val language: LanguageKind = LanguageKind.C_CPP

    /** C/C++ 词法：预处理指令、C++ 原始字符串、数字分隔符。 */
    private val scanner = CStyleScanner(
        CStyleSyntax(
            cppRawStrings = true,
            preprocessor = true,
            digitSeparatorQuote = true,
        ),
    )

    /** 扫描文本后按作用域栈规则统计函数定义数。 */
    override fun analyze(text: CharSequence): LineFunctionCounts {
        val scan = scanner.scan(text)
        return LineFunctionCounts(
            totalLines = scan.totalLines,
            codeLines = scan.codeLines,
            commentLines = scan.commentLines,
            blankLines = scan.blankLines,
            functionCount = countDefinitions(scan.tokens),
        )
    }

    /** 花括号作用域类别。 */
    private enum class Scope {
        /** 可以出现函数定义的声明作用域。 */
        DECLARATION,

        /** 函数体、枚举体、初始化列表等代码块。 */
        CODE,
    }

    /**
     * 统计函数定义数。
     *
     * @param tokens 去除注释与预处理指令后的记号序列。
     * @return 函数定义数量。
     */
    private fun countDefinitions(tokens: List<Token>): Int {
        val parens = matchBrackets(tokens, "(", ")")
        val braces = matchBrackets(tokens, "{", "}")
        val scopes = ArrayDeque<Scope>()
        // 以下状态只在当前声明作用域内有效，遇到 `;` `=` `{` `}` 时重置。
        var candidate = false
        var initializerList = false
        var pendingScope = false
        var pendingEnum = false
        var count = 0
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            val previous = tokens.getOrNull(index - 1)
            val inDeclarationScope = scopes.lastOrNull() != Scope.CODE
            when (token.type) {
                TokenType.IDENT -> when (token.text) {
                    // 类型关键字出现在候选函数头之后，说明前面的 `NAME(...)` 只是无分号的宏调用，不是函数头。
                    "namespace" -> if (inDeclarationScope) {
                        pendingScope = true
                        candidate = false
                    }
                    "class", "struct", "union" -> if (!pendingEnum) {
                        pendingScope = true
                        candidate = false
                    }
                    "enum" -> {
                        pendingEnum = true
                        candidate = false
                    }
                }
                TokenType.PUNCT -> when (token.text) {
                    "(" -> {
                        if (inDeclarationScope && !initializerList && isFunctionName(tokens, index - 1)) {
                            candidate = true
                        }
                        // 参数列表、noexcept(...)、__attribute__((...)) 与初始化列表成员 a(1) 都整段跳过。
                        val close = parens[index]
                        if (close > index) index = close
                    }
                    ":" -> {
                        // 单个 `:`（非 `::`）出现在函数头之后，表示构造函数初始化列表。
                        val isScopeOperator = previous?.isPunct(":") == true || tokens.getOrNull(index + 1)?.isPunct(":") == true
                        if (candidate && !isScopeOperator) initializerList = true
                    }
                    "{" -> {
                        val memberBraceInit = candidate && initializerList && previous != null &&
                            (previous.type == TokenType.IDENT || previous.isPunct(">"))
                        if (memberBraceInit && braces[index] > index) {
                            // 初始化列表中的 `member{value}`，不是函数体，整段跳过。
                            index = braces[index]
                        } else {
                            val scope = when {
                                inDeclarationScope && candidate -> {
                                    count++
                                    Scope.CODE
                                }
                                pendingScope && !pendingEnum -> Scope.DECLARATION
                                inDeclarationScope && previous?.type == TokenType.STRING &&
                                    tokens.getOrNull(index - 2)?.isIdent("extern") == true -> Scope.DECLARATION
                                else -> Scope.CODE
                            }
                            scopes.addLast(scope)
                            candidate = false
                            initializerList = false
                            pendingScope = false
                            pendingEnum = false
                        }
                    }
                    "}" -> {
                        scopes.removeLastOrNull()
                        candidate = false
                        initializerList = false
                        pendingScope = false
                        pendingEnum = false
                    }
                    ";", "=" -> {
                        candidate = false
                        initializerList = false
                        pendingScope = false
                        pendingEnum = false
                    }
                }
                else -> Unit
            }
            index++
        }
        return count
    }

    /**
     * 判断 `(` 前的记号能否构成函数名。
     *
     * 接受普通标识符（含 `Class::method`、`~Class` 的最后一段）以及运算符重载 `operator+`、`operator()`；
     * 排除控制语句、`sizeof`、属性说明符等后面同样跟括号的关键字。
     */
    private fun isFunctionName(tokens: List<Token>, index: Int): Boolean {
        val name = tokens.getOrNull(index) ?: return false
        if (name.type == TokenType.IDENT) return name.text !in NON_FUNCTION_KEYWORDS
        // 运算符重载：向前最多 3 个标点内出现 `operator`，例如 `operator==`、`operator()`、`operator[]`。
        var k = index
        var steps = 0
        while (k >= 0 && steps < 4) {
            val token = tokens[k]
            if (token.isIdent("operator")) return true
            if (token.type != TokenType.PUNCT) return false
            k--
            steps++
        }
        return false
    }

    /** C/C++ 分析器常量。 */
    private companion object {
        /** 后面跟 `(` 但不是函数名的关键字与编译器扩展。 */
        val NON_FUNCTION_KEYWORDS = setOf(
            "if", "for", "while", "switch", "return", "sizeof", "alignof", "decltype", "typeof", "__typeof__",
            "__attribute__", "__declspec", "static_assert", "_Static_assert", "alignas", "_Alignas", "noexcept",
            "throw", "catch", "asm", "__asm__", "__asm", "defined", "_Generic", "requires",
        )
    }
}
