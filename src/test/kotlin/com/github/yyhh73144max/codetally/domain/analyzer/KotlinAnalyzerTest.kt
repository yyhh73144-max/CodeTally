package com.github.yyhh73144max.codetally.domain.analyzer

import com.github.yyhh73144max.codetally.domain.model.LineFunctionCounts
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [KotlinAnalyzer] 的行分类与函数识别测试（纯 JUnit，不启动 IDE）。
 */
class KotlinAnalyzerTest {
    /** 被测分析器。 */
    private val analyzer = KotlinAnalyzer()

    /** 验证空文本统计为 0 行 0 函数，防止空文件被计为 1 个空行。 */
    @Test
    fun emptyTextHasNoLines() {
        assertEquals(LineFunctionCounts(0, 0, 0, 0, 0), analyzer.analyze(""))
    }

    /** 验证末尾换行不额外计行、无末尾换行的最后一行仍计入，以及 CRLF/CR 换行各算一次。 */
    @Test
    fun lineEndingsAreCountedOnce() {
        assertEquals(1, analyzer.analyze("val a = 1\n").totalLines)
        assertEquals(2, analyzer.analyze("val a = 1\nval b = 2").totalLines)
        assertEquals(3, analyzer.analyze("val a = 1\r\n\r\nval b = 2\r\n").totalLines)
        assertEquals(2, analyzer.analyze("val a = 1\rval b = 2").totalLines)
    }

    /** 验证代码、行注释、块注释、KDoc、空行与行尾注释的分类口径。 */
    @Test
    fun classifiesCodeCommentAndBlankLines() {
        val source = """
            |// 头部注释
            |/**
            | * 文档
            | */
            |fun a() = 1 // 行尾注释
            |
            |
            |/* 单行块注释 */ val x = 2
        """.trimMargin()
        val result = analyzer.analyze(source)
        assertEquals(8, result.totalLines)
        assertEquals(2, result.codeLines)
        assertEquals(4, result.commentLines)
        assertEquals(2, result.blankLines)
    }

    /** 验证 Kotlin 嵌套块注释整体被识别为注释，内层注释的结束符不会提前结束外层注释而泄漏代码。 */
    @Test
    fun nestedBlockCommentsAreComments() {
        val source = "/* 外层 /* 内层 */ fun hidden() {} \n 仍是注释 */\nfun visible() {}"
        val result = analyzer.analyze(source)
        assertEquals(2, result.commentLines)
        assertEquals(1, result.codeLines)
        assertEquals(1, result.functionCount)
    }

    /** 验证字符串、原始字符串与模板中的 `fun` 与注释符号不被当作函数或注释。 */
    @Test
    fun stringsDoNotProduceFunctionsOrComments() {
        val source = """
            |val a = "fun fake() // not comment"
            |val b = ${"\"\"\""}
            |   fun alsoFake() /* still string */
            |${"\"\"\""}
            |val c = "${'$'}{ listOf("fun x()").size } fun y()"
        """.trimMargin()
        val result = analyzer.analyze(source)
        assertEquals(5, result.codeLines)
        assertEquals(0, result.commentLines)
        assertEquals(0, result.functionCount)
    }

    /** 验证顶层、成员、扩展、泛型、局部、抽象与匿名函数都计入，lambda 与 fun interface 不计入。 */
    @Test
    fun countsFunctionDeclarations() {
        val source = """
            |fun interface Callback { fun call() }
            |abstract class A {
            |    abstract fun abstractOne(): Int
            |    fun member() {
            |        fun local() = 1
            |        val lambda = { x: Int -> x * 2 }
            |        val anonymous = fun(x: Int): Int { return x }
            |    }
            |}
            |fun String.ext(): Int = length
            |fun <T> generic(t: T): T = t
            |fun `with spaces`() {}
        """.trimMargin()
        // call、abstractOne、member、local、anonymous、ext、generic、with spaces
        assertEquals(8, analyzer.analyze(source).functionCount)
    }

    /** 验证次构造函数计入，带注解/修饰符的主构造函数不计入。 */
    @Test
    fun countsSecondaryConstructorsOnly() {
        val source = """
            |class A @Inject constructor(private val x: Int) {
            |    constructor() : this(0)
            |    @JvmOverloads
            |    private constructor(s: String, t: Int = 1) : this(s.length)
            |}
            |class B private constructor(val y: Int)
        """.trimMargin()
        assertEquals(2, analyzer.analyze(source).functionCount)
    }

    /** 验证块注释与多行字符串中的空白行按空行统计（统一口径：仅含空白的行即空行）。 */
    @Test
    fun whitespaceOnlyLinesInsideCommentsAreBlank() {
        val result = analyzer.analyze("/*\n\n*/")
        assertEquals(LineFunctionCounts(3, 0, 2, 1, 0), result)
    }
}
