package com.github.yyhh73144max.codetally.application

import com.github.yyhh73144max.codetally.domain.model.LanguageKind
import com.intellij.openapi.components.service
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Job

/**
 * [CodeTallyService] 的任务生命周期测试。
 */
class CodeTallyServiceTest : BasePlatformTestCase() {

    /** 在 EDT 上等待任务结束，同时派发事件，避免后台进度需要 EDT 时死锁。 */
    private fun await(job: Job) {
        PlatformTestUtil.waitWithEventsDispatching("统计任务超时", { job.isCompleted }, 60)
    }

    /** 验证一次完整统计发布 Done 状态，且报告包含按语言的汇总与函数数。 */
    fun testTallyPublishesReport() {
        myFixture.addFileToProject("a.kt", "fun a() {}\nfun b() {}\n")
        myFixture.addFileToProject("b.xml", "<a/>\n")
        val service = project.service<CodeTallyService>()
        await(service.startTally(TallyScope.WholeProject))
        val state = service.state.value as TallyState.Done
        val kotlin = state.report.summaries.single { it.language == LanguageKind.KOTLIN }
        assertEquals(2, kotlin.functionCount)
        assertEquals(2, state.report.overall.fileCount)
    }

    /** 验证连续发起两次统计时旧任务被取消、只有最后一次的结果被发布（回归风险：结果乱序覆盖）。 */
    fun testLatestTallyWins() {
        val file = myFixture.addFileToProject("a.kt", "fun a() {}\n").virtualFile
        myFixture.addFileToProject("b.java", "class B { void b() {} }\n")
        val service = project.service<CodeTallyService>()
        val first = service.startTally(TallyScope.WholeProject)
        val second = service.startTally(TallyScope.Selection(listOf(file)))
        await(second)
        assertTrue(first.isCancelled)
        val state = service.state.value as TallyState.Done
        assertEquals(TallyScope.Selection(listOf(file)), state.scope)
        assertEquals(1, state.report.files.size)
    }

    /** 验证取消后状态为 Cancelled，即使任务在开始执行前就被取消也不会停留在 Running。 */
    fun testCancelPublishesCancelled() {
        myFixture.addFileToProject("a.kt", "fun a() {}\n")
        val service = project.service<CodeTallyService>()
        val job = service.startTally(TallyScope.WholeProject)
        service.cancel()
        await(job)
        assertInstanceOf(service.state.value, TallyState.Cancelled::class.java)
    }
}
