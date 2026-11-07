package com.github.yyhh73144max.codetally.domain.model

/**
 * 支持的文件未被纳入统计的原因，用于在报告中给出跳过计数，避免静默丢弃。
 */
enum class SkipReason {
    /** 文件超过大小上限，为避免长时间占用内存与 CPU 而跳过。 */
    TOO_LARGE,

    /** 读取文件内容时发生 I/O 错误。 */
    READ_FAILED,

    /** 分析器内部出现意外异常（属于插件缺陷），该文件被跳过以免影响整体统计。 */
    ANALYSIS_FAILED,
}
