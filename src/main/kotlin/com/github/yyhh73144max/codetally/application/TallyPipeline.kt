package com.github.yyhh73144max.codetally.application

import com.github.yyhh73144max.codetally.data.CollectedFile
import com.github.yyhh73144max.codetally.data.FileTextLoader
import com.github.yyhh73144max.codetally.data.ProjectFileCollector
import com.github.yyhh73144max.codetally.domain.analyzer.AnalyzerRegistry
import com.github.yyhh73144max.codetally.domain.model.FileStats
import com.github.yyhh73144max.codetally.domain.model.SkipReason
import com.github.yyhh73144max.codetally.domain.model.TallyReport
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.reportSequentialProgress
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException

/**
 * 统计流水线：收集文件 → 读取文本 → 按语言分析 → 汇总报告。
 *
 * 只负责一次统计的计算过程，不持有跨次状态；生命周期、并发与状态发布由 [CodeTallyService] 管理。
 * 单个文件的读取失败或分析异常只计入跳过统计，不会中断整体统计。
 *
 * 线程：[run] 应在后台调度器上调用；内部每个文件前检查协程取消。
 *
 * @param project 当前项目。
 * @param registry 语言分析器注册表。
 * @param collector 文件收集器。
 * @param loader 文本读取器。
 */
class TallyPipeline(
    project: Project,
    private val registry: AnalyzerRegistry = AnalyzerRegistry.DEFAULT,
    private val collector: ProjectFileCollector = ProjectFileCollector(project, registry),
    private val loader: FileTextLoader = FileTextLoader(),
) {

    /**
     * 执行一次统计。
     *
     * @param scope 统计范围。
     * @return 统计报告。
     * @throws kotlinx.coroutines.CancellationException 被取消时抛出。
     */
    suspend fun run(scope: TallyScope): TallyReport {
        val roots = when (scope) {
            TallyScope.WholeProject -> null
            is TallyScope.Selection -> scope.roots
        }
        val collected = collector.collect(roots)
        val unsaved = loader.snapshotUnsavedDocuments()
        val stats = ArrayList<FileStats>(collected.files.size)
        val skipped = HashMap<SkipReason, Int>()
        skipped[SkipReason.TOO_LARGE] = collected.oversizedCount

        reportSequentialProgress(collected.files.size) { reporter ->
            for (entry in collected.files) {
                currentCoroutineContext().ensureActive()
                reporter.itemStep(entry.displayPath)
                when (val outcome = analyze(entry, unsaved)) {
                    is Outcome.Success -> stats.add(outcome.stats)
                    is Outcome.Skipped -> skipped.merge(outcome.reason, 1, Int::plus)
                }
            }
        }
        return TallyReport.build(stats, skipped)
    }

    /** 单个文件的处理结果。 */
    private sealed interface Outcome {
        /**
         * 分析成功。
         *
         * @property stats 文件统计。
         */
        class Success(val stats: FileStats) : Outcome

        /**
         * 被跳过。
         *
         * @property reason 跳过原因。
         */
        class Skipped(val reason: SkipReason) : Outcome
    }

    /**
     * 读取并分析单个文件。
     *
     * I/O 失败与分析器意外异常分别归类为跳过原因，并记录不含文件内容的诊断日志。
     */
    private fun analyze(entry: CollectedFile, unsaved: Map<VirtualFile, CharSequence>): Outcome {
        // 收集器已按注册表过滤扩展名；此处为空只可能是收集与分析之间注册表不一致，按分析失败处理。
        val analyzer = registry.forExtension(entry.file.extension) ?: return Outcome.Skipped(SkipReason.ANALYSIS_FAILED)
        val text = try {
            loader.load(entry.file, unsaved)
        } catch (e: IOException) {
            LOG.info("读取文件失败，已跳过：${entry.displayPath}", e)
            return Outcome.Skipped(SkipReason.READ_FAILED)
        }
        val counts = try {
            analyzer.analyze(text)
        } catch (e: RuntimeException) {
            // 分析器约定不抛异常；这里兜底防止单个异常文件拖垮整次统计，并保留异常栈便于定位插件缺陷。
            LOG.warn("分析文件时出现意外异常，已跳过：${entry.displayPath}（${analyzer.language}）", e)
            return Outcome.Skipped(SkipReason.ANALYSIS_FAILED)
        }
        return Outcome.Success(FileStats(entry.displayPath, entry.file.url, analyzer.language, counts))
    }

    /** 流水线日志。 */
    private companion object {
        /** 诊断日志记录器，只记录路径与异常，不记录文件内容。 */
        val LOG = logger<TallyPipeline>()
    }
}
