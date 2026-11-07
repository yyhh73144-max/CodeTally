package com.github.yyhh73144max.codetally.domain.analyzer

/**
 * C 系语法（Kotlin / Java / C / C++）的词法差异开关。
 *
 * @property nestedBlockComments 块注释是否可嵌套（Kotlin 支持）。
 * @property tripleQuoteStrings 是否支持 `"""` 三引号字符串（Kotlin 原始串、Java 文本块）。
 * @property tripleQuoteEscapes 三引号字符串内 `\` 是否为转义符（Java 文本块为 true，Kotlin 原始串为 false）。
 * @property stringTemplates 字符串内 `${...}` 是否为可嵌套的代码模板（Kotlin）。
 * @property cppRawStrings 是否支持 C++ 原始字符串 `R"delim(...)delim"`。
 * @property preprocessor 行首 `#` 是否开启预处理指令（C/C++），指令内容不产出记号但计为代码行。
 * @property backtickIdentifiers 是否支持反引号标识符（Kotlin）。
 * @property dollarInIdentifiers 标识符是否可包含 `$`（Java）。
 * @property digitSeparatorQuote 数字内是否允许 `'` 作为分隔符（C++14）。
 */
internal data class CStyleSyntax(
    val nestedBlockComments: Boolean = false,
    val tripleQuoteStrings: Boolean = false,
    val tripleQuoteEscapes: Boolean = false,
    val stringTemplates: Boolean = false,
    val cppRawStrings: Boolean = false,
    val preprocessor: Boolean = false,
    val backtickIdentifiers: Boolean = false,
    val dollarInIdentifiers: Boolean = false,
    val digitSeparatorQuote: Boolean = false,
)

/**
 * 一次扫描的结果。
 *
 * @property codeLines 代码行数。
 * @property commentLines 纯注释行数。
 * @property blankLines 空行数。
 * @property tokens 去除注释后的记号序列（预处理指令内的记号不包含在内）。
 */
internal class ScanResult(
    val codeLines: Int,
    val commentLines: Int,
    val blankLines: Int,
    val tokens: List<Token>,
) {
    /** 物理行总数，恒等于三类行数之和。 */
    val totalLines: Int get() = codeLines + commentLines + blankLines
}

/**
 * C 系语言共享的轻量词法扫描器：一次线性遍历同时完成行分类与记号化。
 *
 * 设计原因：不依赖 IDE 的 PSI/语言插件，在 Android Studio 未安装 C/C++ 支持时也能统计；
 * 且纯 Kotlin 实现，可在任意后台线程运行并用普通单元测试覆盖。
 * 扫描器能正确跳过注释、字符串、字符字面量、原始串与预处理指令，保证其中的 `{`、`fun`、`//` 等不干扰统计；
 * 对未闭合的普通字符串/字符字面量在行尾自动结束，以便在语法错误文件上仍给出合理结果。
 *
 * 实例无可变状态，可被多个线程并发复用；每次 [scan] 内部创建独立的扫描会话。
 *
 * @param syntax 语言差异开关。
 */
internal class CStyleScanner(private val syntax: CStyleSyntax) {

    /**
     * 扫描整段文本。
     *
     * @param text 源码文本，可包含 `\n`、`\r\n` 或单独 `\r` 换行。
     * @return 行分类计数与记号序列。
     */
    fun scan(text: CharSequence): ScanResult = Session(text).run()

    /** 扫描所处的词法状态。 */
    private enum class Mode { CODE, LINE_COMMENT, BLOCK_COMMENT, STRING, TRIPLE_STRING, CHAR, RAW_STRING }

    /**
     * Kotlin 字符串模板 `${...}` 的帧：记录模板结束后应返回的字符串状态，以及模板内部的花括号深度。
     *
     * @property returnMode 模板结束后恢复的字符串状态。
     */
    private class TemplateFrame(val returnMode: Mode) {
        /** 模板代码中尚未闭合的 `{` 数量；为 0 时遇到 `}` 即结束模板。 */
        var braceDepth = 0
    }

    /**
     * 单次扫描会话，持有全部可变状态，生命周期仅限一次 [scan] 调用。
     *
     * @param text 被扫描的文本。
     */
    private inner class Session(private val text: CharSequence) {
        /** 行分类计数器。 */
        private val lines = LineClassifier()

        /** 已产出的记号。 */
        private val tokens = ArrayList<Token>()

        /** 当前字符下标。 */
        private var i = 0

        /** 当前 0 基行号。 */
        private var line = 0

        /** 当前词法状态。 */
        private var mode = Mode.CODE

        /** 块注释嵌套深度（不支持嵌套的语言最多为 1）。 */
        private var blockDepth = 0

        /** C++ 原始字符串的结束序列，形如 `)delim"`。 */
        private var rawTerminator = ""

        /** 是否处于预处理指令内部（可经由行尾 `\` 跨行）。 */
        private var inPreprocessor = false

        /** 当前行最后一个非空白代码字符，用于判断预处理指令是否以 `\` 续行。 */
        private var lastCodeChar: Char? = null

        /** 最近一个记号的结束下标（不含），用于判断 C++ 原始串前缀是否与引号紧邻。 */
        private var lastTokenEnd = -1

        /** Kotlin 字符串模板栈。 */
        private val templates = ArrayDeque<TemplateFrame>()

        /**
         * 主循环：按状态分派到各扫描函数，统一处理换行。
         *
         * @return 扫描结果。
         */
        fun run(): ScanResult {
            val n = text.length
            while (i < n) {
                val c = text[i]
                if (c == '\n' || c == '\r') {
                    i += if (c == '\r' && i + 1 < n && text[i + 1] == '\n') 2 else 1
                    onLineBreak()
                    continue
                }
                lines.markContent()
                when (mode) {
                    Mode.CODE -> scanCode(c)
                    Mode.LINE_COMMENT -> {
                        if (!c.isWhitespace()) lines.markComment()
                        i++
                    }
                    Mode.BLOCK_COMMENT -> scanBlockComment(c)
                    Mode.STRING -> scanString(c)
                    Mode.TRIPLE_STRING -> scanTripleString(c)
                    Mode.CHAR -> scanChar(c)
                    Mode.RAW_STRING -> scanRawString(c)
                }
            }
            val (code, comment, blank) = lines.finish()
            return ScanResult(code, comment, blank, tokens)
        }

        /** 处理换行：结束行注释与未闭合的单行字面量，判断预处理指令是否续行，并结算当前行。 */
        private fun onLineBreak() {
            when (mode) {
                Mode.LINE_COMMENT -> mode = Mode.CODE
                // 普通字符串/字符字面量不能跨行；遇到换行视为语法错误并恢复到代码状态，避免错误蔓延到整个文件。
                Mode.STRING, Mode.CHAR -> mode = Mode.CODE
                else -> Unit
            }
            // 块注释内换行不结束预处理指令（注释在预处理阶段等价于空格）；只有代码状态下且行尾不是 `\` 才结束。
            if (inPreprocessor && mode == Mode.CODE && lastCodeChar != '\\') inPreprocessor = false
            lastCodeChar = null
            lines.endLine()
            line++
        }

        /** 读取相对当前位置偏移 [offset] 的字符，越界返回 `\u0000`。 */
        private fun peek(offset: Int): Char = text.getOrElse(i + offset) { '\u0000' }

        /**
         * 产出记号；预处理指令内部不产出，避免宏定义中的括号干扰函数识别。
         *
         * @param type 记号类别。
         * @param value 记号文本。
         * @param end 记号结束下标（不含）。
         */
        private fun emit(type: TokenType, value: String, end: Int) {
            lastTokenEnd = end
            if (!inPreprocessor) tokens.add(Token(type, value, line))
        }

        /** 代码状态下的单字符分派。 */
        private fun scanCode(c: Char) {
            if (c.isWhitespace()) {
                i++
                return
            }
            if (c == '/' && peek(1) == '/') {
                mode = Mode.LINE_COMMENT
                lines.markComment()
                i += 2
                return
            }
            if (c == '/' && peek(1) == '*') {
                mode = Mode.BLOCK_COMMENT
                blockDepth = 1
                lines.markComment()
                i += 2
                return
            }
            lines.markCode()
            lastCodeChar = c
            when {
                c == '"' -> startString()
                c == '\'' -> {
                    mode = Mode.CHAR
                    i++
                    emit(TokenType.STRING, "'", i)
                }
                c == '#' && syntax.preprocessor && !inPreprocessor && isFirstCodeCharOnLine() -> {
                    inPreprocessor = true
                    i++
                }
                isIdentifierStart(c) -> scanIdentifier()
                c == '`' && syntax.backtickIdentifiers -> scanBacktickIdentifier()
                c.isDigit() -> scanNumber()
                else -> scanPunct(c)
            }
        }

        /**
         * 判断当前 `#` 是否为本行第一个非空白字符（预处理指令必须位于行首，允许前导空白）。
         *
         * [LineClassifier.markCode] 已在调用前标记过本字符，无法再用行状态判断，因此向前回看到行首是否只有空白。
         */
        private fun isFirstCodeCharOnLine(): Boolean {
            var j = i - 1
            while (j >= 0) {
                val ch = text[j]
                if (ch == '\n' || ch == '\r') return true
                if (!ch.isWhitespace()) return false
                j--
            }
            return true
        }

        /** 处理标点，并维护 Kotlin 字符串模板中的花括号深度。 */
        private fun scanPunct(c: Char) {
            val frame = templates.lastOrNull()
            if (frame != null) {
                if (c == '{') {
                    frame.braceDepth++
                } else if (c == '}') {
                    if (frame.braceDepth == 0) {
                        // 模板 `${...}` 的闭合括号：回到外层字符串，不作为代码记号产出。
                        templates.removeLast()
                        mode = frame.returnMode
                        i++
                        return
                    }
                    frame.braceDepth--
                }
            }
            i++
            emit(TokenType.PUNCT, PUNCT_CACHE.getOrNull(c.code) ?: c.toString(), i)
        }

        /** 处理字符串起始引号：区分三引号串、C++ 原始串与普通字符串。 */
        private fun startString() {
            if (syntax.tripleQuoteStrings && peek(1) == '"' && peek(2) == '"') {
                mode = Mode.TRIPLE_STRING
                i += 3
                emit(TokenType.STRING, "\"", i)
                return
            }
            if (syntax.cppRawStrings && isRawStringPrefix()) {
                val terminator = readRawDelimiter()
                if (terminator != null) {
                    // 前缀 R/LR/u8R 等已作为标识符产出，替换为字符串记号，避免被误认为函数名。
                    if (!inPreprocessor && tokens.isNotEmpty()) tokens.removeAt(tokens.lastIndex)
                    rawTerminator = terminator
                    mode = Mode.RAW_STRING
                    emit(TokenType.STRING, "\"", i)
                    return
                }
            }
            mode = Mode.STRING
            i++
            emit(TokenType.STRING, "\"", i)
        }

        /** 判断引号前紧邻的标识符是否为 C++ 原始字符串前缀。 */
        private fun isRawStringPrefix(): Boolean {
            if (lastTokenEnd != i) return false
            val last = tokens.lastOrNull() ?: return false
            return last.type == TokenType.IDENT && last.text in RAW_STRING_PREFIXES
        }

        /**
         * 读取原始字符串的定界符 `R"delim(`，成功时把位置推进到 `(` 之后。
         *
         * @return 结束序列 `)delim"`；定界符非法（含空白、`\`、超过 16 字符或缺少 `(`）时返回 null 并保持位置不变。
         */
        private fun readRawDelimiter(): String? {
            var j = i + 1
            val delimiter = StringBuilder()
            while (j < text.length && text[j] != '(') {
                val ch = text[j]
                if (ch.isWhitespace() || ch == '\\' || ch == ')' || delimiter.length >= 16) return null
                delimiter.append(ch)
                j++
            }
            if (j >= text.length) return null
            i = j + 1
            return ")$delimiter\""
        }

        /** 扫描标识符或关键字。 */
        private fun scanIdentifier() {
            val start = i
            i++
            while (i < text.length && isIdentifierPart(text[i])) i++
            emit(TokenType.IDENT, text.substring(start, i), i)
        }

        /** 扫描 Kotlin 反引号标识符；遇到换行未闭合时在行尾结束。 */
        private fun scanBacktickIdentifier() {
            val start = i
            i++
            while (i < text.length && text[i] != '`' && text[i] != '\n' && text[i] != '\r') {
                if (!text[i].isWhitespace()) lines.markCode()
                i++
            }
            if (i < text.length && text[i] == '`') i++
            emit(TokenType.IDENT, text.substring(start, i), i)
        }

        /** 扫描数字字面量（含十六进制、后缀、小数点与 C++ 数字分隔符）。 */
        private fun scanNumber() {
            val start = i
            i++
            while (i < text.length) {
                val ch = text[i]
                val accepted = ch.isLetterOrDigit() || ch == '_' || ch == '.' ||
                    (syntax.digitSeparatorQuote && ch == '\'' && text.getOrElse(i + 1) { ' ' }.isLetterOrDigit())
                if (!accepted) break
                i++
            }
            emit(TokenType.NUMBER, text.substring(start, i), i)
        }

        /** 普通字符串内部：处理转义、结束引号与 Kotlin 模板入口。 */
        private fun scanString(c: Char) {
            if (!c.isWhitespace()) lines.markCode()
            when {
                c == '\\' -> skipEscape()
                c == '"' -> {
                    mode = Mode.CODE
                    i++
                }
                syntax.stringTemplates && c == '$' && peek(1) == '{' -> enterTemplate(Mode.STRING)
                else -> i++
            }
        }

        /** 三引号字符串内部：处理结束序列、Java 文本块转义与 Kotlin 模板入口。 */
        private fun scanTripleString(c: Char) {
            if (!c.isWhitespace()) lines.markCode()
            when {
                c == '"' && peek(1) == '"' && peek(2) == '"' -> {
                    i += 3
                    // Kotlin 原始串允许以 `""""` 结尾，多出的引号属于字符串内容。
                    while (i < text.length && text[i] == '"') i++
                    mode = Mode.CODE
                }
                c == '\\' && syntax.tripleQuoteEscapes -> skipEscape()
                syntax.stringTemplates && c == '$' && peek(1) == '{' -> enterTemplate(Mode.TRIPLE_STRING)
                else -> i++
            }
        }

        /** 字符字面量内部。 */
        private fun scanChar(c: Char) {
            if (!c.isWhitespace()) lines.markCode()
            when (c) {
                '\\' -> skipEscape()
                '\'' -> {
                    mode = Mode.CODE
                    i++
                }
                else -> i++
            }
        }

        /** C++ 原始字符串内部：只识别完整结束序列，不处理转义。 */
        private fun scanRawString(c: Char) {
            if (!c.isWhitespace()) lines.markCode()
            if (c == ')' && text.startsWith(rawTerminator, i)) {
                i += rawTerminator.length
                mode = Mode.CODE
            } else {
                i++
            }
        }

        /** 块注释内部：处理结束与（可选的）嵌套。 */
        private fun scanBlockComment(c: Char) {
            if (!c.isWhitespace()) lines.markComment()
            when {
                c == '*' && peek(1) == '/' -> {
                    i += 2
                    blockDepth--
                    if (blockDepth <= 0) mode = Mode.CODE
                }
                syntax.nestedBlockComments && c == '/' && peek(1) == '*' -> {
                    i += 2
                    blockDepth++
                }
                else -> i++
            }
        }

        /** 跳过反斜杠及其后的被转义字符；被转义的若是换行则留给主循环处理，以保证行计数正确。 */
        private fun skipEscape() {
            i++
            if (i < text.length && text[i] != '\n' && text[i] != '\r') i++
        }

        /** 进入 Kotlin 字符串模板 `${`，模板内按代码扫描。 */
        private fun enterTemplate(returnMode: Mode) {
            templates.addLast(TemplateFrame(returnMode))
            mode = Mode.CODE
            i += 2
        }

        /** 标识符首字符判定。 */
        private fun isIdentifierStart(c: Char): Boolean =
            c.isLetter() || c == '_' || (c == '$' && syntax.dollarInIdentifiers)

        /** 标识符后续字符判定。 */
        private fun isIdentifierPart(c: Char): Boolean =
            c.isLetterOrDigit() || c == '_' || (c == '$' && syntax.dollarInIdentifiers)
    }

    /** 扫描器共享常量。 */
    private companion object {
        /** C++ 原始字符串允许的前缀。 */
        val RAW_STRING_PREFIXES = setOf("R", "LR", "uR", "UR", "u8R")

        /** ASCII 单字符字符串缓存，避免为每个标点分配新字符串。 */
        val PUNCT_CACHE: Array<String> = Array(128) { it.toChar().toString() }
    }
}
