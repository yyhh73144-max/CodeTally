package com.github.yyhh73144max.codetally.data

import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * 文件文本读取器：统计应反映用户在编辑器中看到的内容，因此未保存的文档优先于磁盘内容。
 *
 * 磁盘内容一律按 UTF-8 解码并去掉 BOM。对于非 UTF-8 编码（如 GBK）的文件，非法字节会被替换为占位符，
 * 但换行符、注释符号与关键字均为 ASCII，不影响行数与函数数统计。
 *
 * 线程：两个方法都应在后台线程/协程中调用；[load] 会执行磁盘 I/O。
 */
class FileTextLoader {

    /**
     * 在一次读操作中抓取所有未保存文档的不可变文本快照，避免对每个文件单独加读锁。
     *
     * @return 文件到未保存文本的映射；文本是不可变快照，可在读锁外安全使用。
     */
    suspend fun snapshotUnsavedDocuments(): Map<VirtualFile, CharSequence> = readAction {
        val manager = FileDocumentManager.getInstance()
        manager.unsavedDocuments.mapNotNull { document ->
            manager.getFile(document)?.let { file -> file to document.immutableCharSequence }
        }.toMap()
    }

    /**
     * 读取文件文本。
     *
     * @param file 目标文件。
     * @param unsaved [snapshotUnsavedDocuments] 返回的快照。
     * @return 未保存文本或磁盘内容（UTF-8 解码、去 BOM）。
     * @throws IOException 磁盘读取失败时抛出，由调用方计入跳过统计。
     */
    @Throws(IOException::class)
    fun load(file: VirtualFile, unsaved: Map<VirtualFile, CharSequence>): CharSequence {
        unsaved[file]?.let { return it }
        val text = String(file.contentsToByteArray(), StandardCharsets.UTF_8)
        return if (text.startsWith(BOM)) text.substring(1) else text
    }

    /** 读取器常量。 */
    private companion object {
        /** UTF-8 BOM 解码后的字符。 */
        const val BOM = '﻿'
    }
}
