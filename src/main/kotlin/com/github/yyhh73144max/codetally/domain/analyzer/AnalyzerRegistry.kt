package com.github.yyhh73144max.codetally.domain.analyzer

import com.github.yyhh73144max.codetally.domain.model.LanguageKind

/**
 * 扩展名到语言分析器的路由表。
 *
 * 分析器均无状态且线程安全，因此注册表持有单例实例供所有统计任务共享。
 * 新增语言时只需实现 [LanguageAnalyzer]、在 [LanguageKind] 中声明扩展名并在此注册。
 *
 * @param analyzers 参与路由的分析器；每种语言至多一个，扩展名不得在语言之间重复。
 */
class AnalyzerRegistry(analyzers: List<LanguageAnalyzer>) {
    /** 小写扩展名到分析器的映射。 */
    private val byExtension: Map<String, LanguageAnalyzer> = buildMap {
        for (analyzer in analyzers) {
            for (extension in analyzer.language.extensions) {
                val existing = put(extension, analyzer)
                require(existing == null) { "扩展名 .$extension 被重复注册到 ${existing?.language} 与 ${analyzer.language}" }
            }
        }
    }

    /**
     * 按扩展名查找分析器。
     *
     * @param extension 文件扩展名（不含点），大小写不敏感；null 表示无扩展名。
     * @return 对应分析器；不支持的扩展名返回 null，调用方应忽略该文件。
     */
    fun forExtension(extension: String?): LanguageAnalyzer? =
        extension?.let { byExtension[it.lowercase()] }

    /** 默认注册表提供者。 */
    companion object {
        /** 包含全部内置语言分析器的默认注册表。 */
        val DEFAULT: AnalyzerRegistry = AnalyzerRegistry(
            listOf(KotlinAnalyzer(), JavaAnalyzer(), CAnalyzer(), XmlAnalyzer()),
        )
    }
}
