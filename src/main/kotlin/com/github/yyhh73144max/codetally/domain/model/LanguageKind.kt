package com.github.yyhh73144max.codetally.domain.model

/**
 * 插件支持统计的语言类别。
 *
 * 每个类别绑定一组小写扩展名（不含点），由 [com.github.yyhh73144max.codetally.domain.analyzer.AnalyzerRegistry]
 * 用来把文件路由到对应的词法分析器。枚举声明顺序即 UI 汇总表中的展示顺序。
 *
 * @property displayName 在工具窗口中展示的语言名称。
 * @property extensions 归属于该语言的扩展名集合，全部为小写。
 * @property supportsFunctions 该语言是否有“函数”概念；为 false 时函数数统计为“不适用”（例如 XML）。
 */
enum class LanguageKind(
    val displayName: String,
    val extensions: Set<String>,
    val supportsFunctions: Boolean,
) {
    /** Kotlin 源码与 Kotlin 脚本（含 `build.gradle.kts`）。 */
    KOTLIN("Kotlin", setOf("kt", "kts"), true),

    /** Java 源码。 */
    JAVA("Java", setOf("java"), true),

    /** C / C++ 源码与头文件，NDK/JNI 代码统一归入此类。 */
    C_CPP("C/C++", setOf("c", "h", "cpp", "cc", "cxx", "hpp", "hh", "hxx", "inl"), true),

    /** XML 文件（布局、资源、清单等），没有函数概念。 */
    XML("XML", setOf("xml"), false),
}
