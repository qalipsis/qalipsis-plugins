/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.plugins.slack.notification

import com.slack.api.Slack
import com.slack.api.methods.AsyncMethodsClient
import com.slack.api.methods.SlackApiException
import com.slack.api.methods.response.chat.ChatPostMessageResponse
import com.slack.api.model.Attachments.asAttachments
import com.slack.api.model.Attachments.attachment
import com.slack.api.model.block.Blocks.asBlocks
import com.slack.api.model.block.Blocks.context
import com.slack.api.model.block.Blocks.divider
import com.slack.api.model.block.Blocks.section
import com.slack.api.model.block.LayoutBlock
import com.slack.api.model.block.composition.BlockCompositions.markdownText
import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.CampaignReportPublisher
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.sync.asSuspended
import jakarta.inject.Singleton
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import javax.annotation.PostConstruct


/**
 * Custom implementation of a [CampaignReportPublisher] that sends a Slack notification
 * at the end of a campaign.
 *
 * @author Francisca Eze
 */
@Singleton
@Requirements(
    Requires(beans = [SlackNotificationConfiguration::class])
)
internal class SlackNotificationPublisher(
    val configuration: SlackNotificationConfiguration
) : CampaignReportPublisher {

    @KTestable
    private lateinit var asyncSlackMethodsClient: AsyncMethodsClient

    @PostConstruct
    fun init() {
        asyncSlackMethodsClient = Slack.getInstance().methodsAsync(configuration.token)
    }

    override suspend fun publish(tenant: String, campaignKey: CampaignKey, report: CampaignReport) {
        // subscribe to notification
        val reportStatus = runCatching { ReportExecutionStatus.valueOf("${report.status}") }.getOrNull()
        if (reportStatus != null && ((configuration.status.contains(ReportExecutionStatus.ALL)) || configuration.status.contains(
                ReportExecutionStatus.valueOf(reportStatus.toString())
            ))
        ) sendNotification(campaignKey, report)
    }

    private suspend fun sendNotification(campaignKey: CampaignKey, report: CampaignReport) {
        try {
            postChatMessageRequest(report).asSuspended().get()
            logger.info { "Notification sent" }
        } catch (requestFailureException: SlackApiException) {
            logger.error { "Failed to send notification: ${requestFailureException.message}" }
        } catch (connectivityException: IOException) {
            logger.error { "Failed to send notification: ${connectivityException.message}" }
        }
    }

    @KTestable
    private fun postChatMessageRequest(report: CampaignReport): CompletableFuture<ChatPostMessageResponse> {
        val color = statusColor(report.status)
        val dateRange = formatDateRange(report.start, report.end)
        val duration = formatDuration(report.start, report.end)
        val startedMinions = report.startedMinions.formatted()
        val completedMinions = report.completedMinions.formatted()
        val successfulExecutions = report.successfulExecutions.formatted()
        val failedExecutions = report.failedExecutions.formatted()
        val rate = failureRate(report.successfulExecutions, report.failedExecutions)

        val minionsText = markdownText("*Minions*\n↑ $startedMinions started  ✓ $completedMinions done")
        val executionsText =
            markdownText("*Executions*\n✅ $successfulExecutions  ❌ $failedExecutions  ·  *${rate}% fail*")

        val blocks = mutableListOf<LayoutBlock>(
            section { s -> s.text(markdownText("*${report.campaignKey}*  `${report.status}`")) },
            context { c -> c.elements(listOf(markdownText("$dateRange  ·  $duration"))) },
            section { s -> s.fields(listOf(minionsText, executionsText)) }
        )

        if (report.scenariosReports.isNotEmpty()) {
            blocks += divider()
            val scenarioLines = report.scenariosReports.joinToString("\n") { s ->
                val sRate = failureRate(s.successfulExecutions, s.failedExecutions)
                "${statusEmoji(s.status)} ${s.scenarioName}  `${s.status}`  ✅ ${s.successfulExecutions.formatted()} ❌ ${s.failedExecutions.formatted()} · *${sRate}% fail*"
            }
            blocks += section { s -> s.text(markdownText("*Scenarios*\n$scenarioLines")) }
        }

        return asyncSlackMethodsClient.chatPostMessage { req ->
            req
                .token(configuration.token)
                .channel(configuration.channel)
                .attachments(
                    asAttachments(attachment {
                        it
                            .color(color)
                            .fallback("${report.campaignKey} ${report.status}")
                            .blocks(asBlocks(*blocks.toTypedArray()))
                    })
                )
        }
    }

    private fun Int?.formatted() = this?.let { "%,d".format(it) } ?: "—"

    private fun failureRate(ok: Int?, fail: Int?): Int {
        val total = (ok ?: 0) + (fail ?: 0)
        return if (total > 0) (fail ?: 0) * 100 / total else 0
    }

    private fun formatDateRange(start: Instant?, end: Instant?): String {
        val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneOffset.UTC)
        val s = start?.let { fmt.format(it) } ?: return "—"
        val e = end?.let { fmt.format(it) }
        return if (e != null && e != s) "$s → $e" else s
    }

    private fun formatDuration(start: Instant?, end: Instant?): String {
        if (start == null || end == null) return "—"
        val d = Duration.between(start, end)
        return when {
            d.toDays() >= 1 -> "${d.toDays()}d"
            d.toHours() >= 1 -> "${d.toHours()}h ${d.toMinutesPart()}m"
            d.toMinutes() >= 1 -> "${d.toMinutes()}m ${d.toSecondsPart()}s"
            else -> "${d.seconds}s"
        }
    }

    private fun statusEmoji(status: ExecutionStatus) = when (status) {
        ExecutionStatus.SUCCESSFUL -> ":large_green_circle:"
        ExecutionStatus.WARNING -> ":large_yellow_circle:"
        ExecutionStatus.ABORTED -> ":white_circle:"
        else -> ":red_circle:"
    }

    private fun statusColor(status: ExecutionStatus) = when (status) {
        ExecutionStatus.SUCCESSFUL -> SUCCESS_COLOR
        ExecutionStatus.WARNING -> WARNING_COLOR
        else -> FAILURE_COLOR
    }

    companion object {
        private const val SUCCESS_COLOR = "#36a64f"
        private const val FAILURE_COLOR = "#bf0606"
        private const val WARNING_COLOR = "#e69d0b"
        private val logger = logger()
    }

}
