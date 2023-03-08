/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.plugins.slack.notification

import com.slack.api.Slack
import com.slack.api.methods.AsyncMethodsClient
import com.slack.api.methods.SlackApiException
import com.slack.api.methods.response.chat.ChatPostMessageResponse
import com.slack.api.model.Attachments.asAttachments
import com.slack.api.model.Attachments.attachment
import com.slack.api.model.block.Blocks.asBlocks
import com.slack.api.model.block.Blocks.header
import com.slack.api.model.block.Blocks.section
import com.slack.api.model.block.SectionBlock
import com.slack.api.model.block.composition.BlockCompositions.markdownText
import com.slack.api.model.block.composition.BlockCompositions.plainText
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

    override suspend fun publish(campaignKey: CampaignKey, report: CampaignReport) {
        // subscribe to notification
        val reportStatus = ReportExecutionStatus.values().firstOrNull { it.name === report.status.toString() }
        if (reportStatus != null && ((configuration.status.contains(ReportExecutionStatus.ALL)) || configuration.status.contains(
                ReportExecutionStatus.valueOf(reportStatus.toString())
            ))
        ) sendNotification(campaignKey, report)
    }

    private suspend fun sendNotification(campaignKey: CampaignKey, report: CampaignReport) {
        try {
            val messageBody = composeMessageBody(report)
            val colorScheme = when (report.status) {
                ExecutionStatus.SUCCESSFUL -> Pair(SUCCESS_COLOR, SUCCESS_MARK)
                ExecutionStatus.WARNING -> Pair(WARNING_COLOR, WARNING_MARK)
                else -> Pair(FAILURE_COLOR, FAILURE_MARK)
            }
            postChatMessageRequest(campaignKey, report, messageBody, colorScheme).asSuspended().get()
            logger.info { "Notification sent" }
        } catch (requestFailureException: SlackApiException) {
            logger.error { "Failed to send notification: ${requestFailureException.message}" }
        } catch (connectivityException: IOException) {
            logger.error { "Failed to send notification: ${connectivityException.message}" }
        }
    }

    private fun composeMessageBody(report: CampaignReport): String {
        val duration = report.end?.let { Duration.between(report.start, it).toSeconds() }
        return """
            *Campaign*.......................................${report.campaignKey}
            *Start*.................................................${report.start}
            *End*...................................................${report.end ?: RUNNING_INDICATOR}
            *Duration*.........................................${duration?.let { "$it seconds" } ?: RUNNING_INDICATOR}
            *Started minions*............................${report.startedMinions}
            *Completed minions*.....................${report.completedMinions}
            *Successful steps executions*......${report.successfulExecutions}
            *Failed steps executions*...............${report.failedExecutions}
            *Status*..............................................${report.status}
        """.trimIndent()
    }

    @KTestable
    private fun postChatMessageRequest(
        campaignKey: CampaignKey,
        report: CampaignReport,
        messageBody: String,
        colorScheme: Pair<String, String>
    ): CompletableFuture<ChatPostMessageResponse> {
        val (color, emoji) = colorScheme
        return asyncSlackMethodsClient.chatPostMessage { req ->
            req
                .token(configuration.token)
                .channel(configuration.channel)
                .blocks(
                    asBlocks(header {
                        it.text(plainText("$campaignKey ${report.status} $emoji", true))
                    })
                )
                .attachments(
                    asAttachments(attachment {
                        it
                            .color(color)
                            .fallback("$campaignKey ${report.status}")
                            .blocks(
                                asBlocks(
                                    section { s: SectionBlock.SectionBlockBuilder ->
                                        s.text(markdownText(messageBody))
                                    }
                                )
                            )
                    })
                )
        }
    }

    companion object {
        private const val RUNNING_INDICATOR = "<Running>"
        private const val SUCCESS_COLOR = "#36a64f"
        private const val FAILURE_COLOR = "#bf0606"
        private const val WARNING_COLOR = "#e69d0b"
        private const val SUCCESS_MARK = ":large_green_circle:"
        private const val WARNING_MARK = ":large_orange_circle:"
        private const val FAILURE_MARK = ":red_circle:"
        private val logger = logger()
    }

}