package io.qalipsis.plugins.slack.notification

import io.qalipsis.api.report.ExecutionStatus

/**
 * Report Status for when to trigger notifications.
 *
 * @property ALL triggers notification for successful, failed, warning and aborted campaign statuses
 * @property SUCCESSFUL triggers notification for successful campaign executions only
 * @property WARNING triggers notification for campaign executions with warning status only
 * @property FAILED triggers notification for failed campaign executions only
 * @property ABORTED triggers notification for campaign executions with aborted status only
 *
 * @author Francisca Eze
 */
enum class ReportExecutionStatus(val supportedCampaignStatus: Set<ExecutionStatus>) {
    ALL(setOf(ExecutionStatus.FAILED, ExecutionStatus.WARNING, ExecutionStatus.SUCCESSFUL, ExecutionStatus.ABORTED)),
    FAILED(setOf(ExecutionStatus.FAILED)),
    SUCCESSFUL(setOf(ExecutionStatus.SUCCESSFUL)),
    WARNING(setOf(ExecutionStatus.WARNING)),
    ABORTED(setOf(ExecutionStatus.ABORTED))
}