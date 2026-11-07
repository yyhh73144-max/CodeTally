package com.github.yyhh73144max.codetally.application

import com.github.yyhh73144max.codetally.domain.model.TallyReport

/**
 * 统计任务的对外状态，由 [CodeTallyService] 唯一持有并通过 StateFlow 发布给 UI。
 */
sealed interface TallyState {
    /** 尚未执行过统计。 */
    data object Idle : TallyState

    /**
     * 统计进行中。
     *
     * @property scope 正在统计的范围。
     */
    data class Running(val scope: TallyScope) : TallyState

    /**
     * 统计完成。
     *
     * @property scope 统计范围。
     * @property report 统计报告。
     */
    data class Done(val scope: TallyScope, val report: TallyReport) : TallyState

    /**
     * 统计被用户取消（被新的统计替换时不会发布此状态，而是直接进入新的 [Running]）。
     *
     * @property scope 被取消的统计范围。
     */
    data class Cancelled(val scope: TallyScope) : TallyState

    /**
     * 统计因意外异常失败。
     *
     * @property scope 统计范围。
     * @property message 面向用户的错误摘要，不含文件内容。
     */
    data class Failed(val scope: TallyScope, val message: String) : TallyState
}
