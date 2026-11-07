package com.github.yyhh73144max.codetally.domain.model

/**
 * 单个文件的统计结果。
 *
 * 领域层不持有 IDE 的 `VirtualFile`，只保存可序列化的字符串标识，展示层需要打开文件时再按 [fileUrl] 解析。
 *
 * @property displayPath 展示用路径，通常为相对项目根目录的路径。
 * @property fileUrl 文件的 VFS URL，用于在 UI 中定位并打开文件。
 * @property language 文件所属语言。
 * @property counts 行数与函数数统计。
 */
data class FileStats(
    val displayPath: String,
    val fileUrl: String,
    val language: LanguageKind,
    val counts: LineFunctionCounts,
)
