package com.github.yyhh73144max.codetally.application

import com.github.yyhh73144max.codetally.CodeTallyBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 项目级统计服务：统计任务生命周期与结果状态的唯一所有者。
 *
 * - 同一时间只运行一个统计任务：发起新统计会取消上一个任务（单一 Job），避免重复计算与结果乱序；
 * - 只有“当前任务”才能发布状态，被替换的旧任务即使稍后结束也不会覆盖新状态；
 * - 统计在后台调度器执行并显示可取消的后台进度；服务的协程作用域由平台注入，项目关闭时自动取消。
 *
 * 线程：所有公开方法线程安全，可在 EDT 或后台线程调用且不会阻塞。
 *
 * @param project 当前项目。
 * @param coroutineScope 平台注入的服务级协程作用域。
 */
@Service(Service.Level.PROJECT)
class CodeTallyService(private val project: Project, private val coroutineScope: CoroutineScope) {

    /** 统计流水线；无跨次状态，可复用。 */
    private val pipeline = TallyPipeline(project)

    /** 状态的可变源，仅在 [lock] 保护下由当前任务写入。 */
    private val mutableState = MutableStateFlow<TallyState>(TallyState.Idle)

    /** 对外只读的统计状态流，新订阅者立即收到最新状态。 */
    val state: StateFlow<TallyState> = mutableState.asStateFlow()

    /** 保护 [currentJob] 与状态发布的原子性。 */
    private val lock = Any()

    /** 当前（最近一次发起的）统计任务。 */
    private var currentJob: Job? = null

    /**
     * 发起一次统计；若已有统计在运行则先取消它。
     *
     * @param scope 统计范围。
     * @return 新任务的 Job，便于调用方（如测试）等待完成；UI 应通过 [state] 观察结果。
     */
    fun startTally(scope: TallyScope): Job = synchronized(lock) {
        val previous = currentJob
        previous?.cancel()
        // LAZY 启动：确保任务体运行前 currentJob 已指向自身，从而“是否为当前任务”的判断不存在竞态。
        val job = coroutineScope.launch(start = CoroutineStart.LAZY) {
            // 等待旧任务完全退出，避免两次统计同时占用 CPU 与读锁。
            previous?.join()
            runTally(scope)
        }
        // 在完成回调中发布“已取消”：即使任务在任务体开始执行前就被取消（任务体永不运行），状态也不会停留在旧的 Running。
        // 被新任务替换的旧任务因不再是当前任务，publish 会忽略它。
        job.invokeOnCompletion { cause ->
            if (cause != null) publish(job, TallyState.Cancelled(scope))
        }
        currentJob = job
        job.start()
        job
    }

    /** 取消正在运行的统计（若有）；取消后状态变为 [TallyState.Cancelled]。 */
    fun cancel() {
        synchronized(lock) { currentJob?.cancel() }
    }

    /**
     * 执行统计并按结果发布状态。
     *
     * 取消（含平台的 ControlFlowException）直接向上抛出以遵守协程取消约定，状态由完成回调发布；
     * 其他异常记录日志并发布 [TallyState.Failed]。
     */
    private suspend fun runTally(scope: TallyScope) {
        val self = currentCoroutineContext().job
        publish(self, TallyState.Running(scope))
        try {
            val report = withBackgroundProgress(project, CodeTallyBundle.message("progress.title")) {
                withContext(Dispatchers.Default) { pipeline.run(scope) }
            }
            publish(self, TallyState.Done(scope, report))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is ControlFlowException) throw e
            LOG.warn("代码统计失败", e)
            publish(self, TallyState.Failed(scope, e.message ?: e.javaClass.simpleName))
        }
    }

    /** 仅当 [job] 仍是当前任务时发布状态，防止被替换的旧任务覆盖新结果。 */
    private fun publish(job: Job, newState: TallyState) {
        synchronized(lock) {
            if (currentJob === job) mutableState.value = newState
        }
    }

    /** 服务日志。 */
    private companion object {
        /** 诊断日志记录器。 */
        val LOG = logger<CodeTallyService>()
    }
}
