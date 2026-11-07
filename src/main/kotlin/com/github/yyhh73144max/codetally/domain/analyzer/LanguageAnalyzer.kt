package com.github.yyhh73144max.codetally.domain.analyzer

import com.github.yyhh73144max.codetally.domain.model.LanguageKind
import com.github.yyhh73144max.codetally.domain.model.LineFunctionCounts

/**
 * 单一语言的源码分析器：根据文本计算行分类与函数数。
 *
 * 实现必须是无状态、线程安全的纯函数，不访问文件系统或 IDE；调用方负责读取文本并在后台线程调用。
 * 输入为任意文本（可能含语法错误），实现不得抛出异常，应尽量给出合理的近似结果。
 */
interface LanguageAnalyzer {
    /** 本分析器处理的语言。 */
    val language: LanguageKind

    /**
     * 分析一段源码文本。
     *
     * @param text 完整文件内容。
     * @return 行数与函数数统计；语言不支持函数时 [LineFunctionCounts.functionCount] 为 null。
     */
    fun analyze(text: CharSequence): LineFunctionCounts
}
