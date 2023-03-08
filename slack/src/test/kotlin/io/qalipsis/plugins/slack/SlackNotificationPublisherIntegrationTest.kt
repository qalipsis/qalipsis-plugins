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

package io.qalipsis.plugins.slack

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import com.slack.api.methods.AsyncMethodsClient
import com.slack.api.model.Attachment
import com.slack.api.model.Message
import com.slack.api.model.block.HeaderBlock
import com.slack.api.model.block.SectionBlock
import com.slack.api.model.block.composition.MarkdownTextObject
import com.slack.api.model.block.composition.PlainTextObject
import io.micronaut.context.annotation.Value
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.coExcludeRecords
import io.mockk.coVerifyAll
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.sync.asSuspended
import io.qalipsis.plugins.slack.notification.SlackNotificationConfiguration
import io.qalipsis.plugins.slack.notification.SlackNotificationPublisher
import io.qalipsis.plugins.slack.notification.ReportExecutionStatus
import io.qalipsis.plugins.slack.notification.catadioptre.asyncSlackMethodsClient
import io.qalipsis.plugins.slack.notification.catadioptre.postChatMessageRequest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.time.Instant

@WithMockk
@MicronautTest(startApplication = false, propertySources = ["classpath:application-slack-test.yml"])
internal class SlackNotificationPublisherIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Value("\${report.export.slack.token}")
    private lateinit var botToken: String

    private lateinit var spiedSlackClient: AsyncMethodsClient

    private lateinit var slackNotificationPublisher: SlackNotificationPublisher

    private lateinit var campaignReportPrototype: CampaignReport

    private lateinit var slackNotificationConfiguration: SlackNotificationConfiguration

    private lateinit var mockNotificationPublisher: SlackNotificationPublisher

    @BeforeEach
    internal fun setupAll() {
        campaignReportPrototype = CampaignReport(
            campaignKey = "Campaign-1",
            startedMinions = 1000,
            completedMinions = 990,
            successfulExecutions = 990,
            failedExecutions = 10,
            status = ExecutionStatus.SUCCESSFUL,
            scheduledMinions = 4,
            start = Instant.parse("2022-10-29T00:00:00.00Z"),
            end = Instant.parse("2022-11-05T00:00:00.00Z")
        )

        slackNotificationConfiguration = mockk {
            every { enabled } returns true
            every { channel } returns "slack-plugin-test"
            every { status } returns setOf(ReportExecutionStatus.ALL)
            every { url } returns "https://slack.com/api/chat.postMessage"
            every { token } returns botToken
        }
        slackNotificationPublisher =
            SlackNotificationPublisher(slackNotificationConfiguration)
        slackNotificationPublisher.init()
        spiedSlackClient = spyk(slackNotificationPublisher.asyncSlackMethodsClient())
        slackNotificationPublisher.asyncSlackMethodsClient(spiedSlackClient)
        mockNotificationPublisher = spyk(SlackNotificationPublisher(slackNotificationConfiguration), recordPrivateCalls = true)

        coExcludeRecords { mockNotificationPublisher.configuration }
        coExcludeRecords { mockNotificationPublisher.publish(any(), any()) }
    }

    @Test
    fun `should send notification for successful campaign with appropriate color scheme`() =
        testDispatcherProvider.run {
            // given
            val campaignReport = campaignReportPrototype.copy(failedExecutions = 0)
            val message = composeMessage(campaignReport)
            val colorScheme = getColorScheme(campaignReport.status)

            // when
            val response = slackNotificationPublisher.postChatMessageRequest(
                campaignReport.campaignKey,
                campaignReport,
                message,
                colorScheme
            ).asSuspended().get()

            // then
            val retrievedMessage: Message = retrieveMessage(response.channel, response.ts)
            val headerBlock = retrievedMessage.blocks[0] as HeaderBlock
            val attachmentBlock = retrievedMessage.attachments[0]
            assertThat(headerBlock.text).isEqualTo(
                PlainTextObject(
                    "${campaignReport.campaignKey} ${campaignReport.status} ${colorScheme.second}",
                    true
                )
            )
            assertThat(attachmentBlock.blocks[0] as SectionBlock).all {
                prop(SectionBlock::getType).isEqualTo("section")
                prop(SectionBlock::getText).isEqualTo(MarkdownTextObject(message, false))
            }
            assertThat(attachmentBlock).all {
                prop(Attachment::getFallback).isEqualTo("${campaignReport.campaignKey} ${campaignReport.status}")
                prop(Attachment::getColor).isEqualTo(colorScheme.first)
            }
        }

    @Test
    fun `should send notification for a failed campaign with appropriate color scheme`() = testDispatcherProvider.run {
        // given
        val campaignReport = campaignReportPrototype.copy(
            status = ExecutionStatus.FAILED,
            failedExecutions = 900,
            successfulExecutions = 100,
            completedMinions = 100,
            campaignKey = "Campaign-2"
        )
        val message = composeMessage(campaignReport)
        val colorScheme = getColorScheme(campaignReport.status)

        // when
        val response = slackNotificationPublisher.postChatMessageRequest(
            campaignReport.campaignKey,
            campaignReport,
            message,
            colorScheme
        ).asSuspended().get()

        // then
        val retrievedMessage: Message = retrieveMessage(response.channel, response.ts)
        val headerBlock = retrievedMessage.blocks[0] as HeaderBlock
        val attachmentBlock = retrievedMessage.attachments[0]
        assertThat(headerBlock.text).isEqualTo(
            PlainTextObject(
                "${campaignReport.campaignKey} ${campaignReport.status} ${colorScheme.second}",
                true
            )
        )
        assertThat(attachmentBlock.blocks[0] as SectionBlock).all {
            prop(SectionBlock::getType).isEqualTo("section")
            prop(SectionBlock::getText).isEqualTo(MarkdownTextObject(message, false))
        }
        assertThat(attachmentBlock).all {
            prop(Attachment::getFallback).isEqualTo("${campaignReport.campaignKey} ${campaignReport.status}")
            prop(Attachment::getColor).isEqualTo(colorScheme.first)
        }
    }

    @Test
    fun `should send notification for a campaign with warning status and with appropriate color scheme`() =
        testDispatcherProvider.run {
            // when
            val campaignReport = campaignReportPrototype.copy(
                status = ExecutionStatus.WARNING,
                failedExecutions = 500,
                successfulExecutions = 200,
                completedMinions = 200,
                startedMinions = 700,
                campaignKey = "Campaign-3"
            )
            val message = composeMessage(campaignReport)
            val colorScheme = getColorScheme(campaignReport.status)

            // when
            val response = slackNotificationPublisher.postChatMessageRequest(
                campaignReport.campaignKey,
                campaignReport,
                message,
                colorScheme
            ).asSuspended().get()

            // then
            val retrievedMessage: Message = retrieveMessage(response.channel, response.ts)
            val headerBlock = retrievedMessage.blocks[0] as HeaderBlock
            val attachmentBlock = retrievedMessage.attachments[0]
            assertThat(headerBlock.text).isEqualTo(
                PlainTextObject(
                    "${campaignReport.campaignKey} ${campaignReport.status} ${colorScheme.second}",
                    true
                )
            )
            assertThat(attachmentBlock.blocks[0] as SectionBlock).all {
                prop(SectionBlock::getType).isEqualTo("section")
                prop(SectionBlock::getText).isEqualTo(MarkdownTextObject(message, false))
            }
            assertThat(attachmentBlock).all {
                prop(Attachment::getFallback).isEqualTo("${campaignReport.campaignKey} ${campaignReport.status}")
                prop(Attachment::getColor).isEqualTo(colorScheme.first)
            }
        }

    @Test
    fun `should not send notification when campaign report status is not in the list of subscribed statuses`() =
        testDispatcherProvider.run {
            //given
            every { slackNotificationConfiguration.status } returns setOf(
                ReportExecutionStatus.ABORTED,
                ReportExecutionStatus.FAILED
            )
            val campaignReport = campaignReportPrototype.copy(campaignKey = "Campaign-4", status = ExecutionStatus.SUCCESSFUL)
            coEvery { mockNotificationPublisher["sendNotification"](any<String>(), any<CampaignReport>()) } returns Unit

            // when
            mockNotificationPublisher.publish(campaignReport.campaignKey, campaignReport)

            //then
            coVerifyNever { mockNotificationPublisher["sendNotification"](campaignReport.campaignKey, campaignReport) }
        }

    @Test
    fun `should not send notification when campaign report status is not in the list of known report statuses`() =
        testDispatcherProvider.run {
            //given
            every { slackNotificationConfiguration.status } returns setOf(
                ReportExecutionStatus.ABORTED,
                ReportExecutionStatus.FAILED
            )
            val campaignReport =
                campaignReportPrototype.copy(campaignKey = "Campaign-5", status = ExecutionStatus.QUEUED)
            coEvery { mockNotificationPublisher["sendNotification"](any<String>(), any<CampaignReport>()) } returns Unit

            // when
            mockNotificationPublisher.publish(campaignReport.campaignKey, campaignReport)

            //then
            coVerifyNever { mockNotificationPublisher["sendNotification"](campaignReport.campaignKey, campaignReport) }
        }

    @Test
    fun `should send notification when campaign report status is in the list of subscribed statuses`() =
        testDispatcherProvider.run {
            //given
            every { slackNotificationConfiguration.status } returns setOf(
                ReportExecutionStatus.ABORTED,
                ReportExecutionStatus.FAILED
            )
            val campaignReport =
                campaignReportPrototype.copy(campaignKey = "Campaign-6", status = ExecutionStatus.ABORTED)
            coEvery { mockNotificationPublisher["sendNotification"](any<String>(), any<CampaignReport>()) } returns Unit

            // when
            mockNotificationPublisher.publish(campaignReport.campaignKey, campaignReport)

            //then
            coVerifyOnce { mockNotificationPublisher["sendNotification"](campaignReport.campaignKey, campaignReport) }
        }

    @Test
    fun `should send an email when campaign report status is in the list of subscribed statuses`() =
        testDispatcherProvider.run {
            //given
            val campaignReport1 =
                campaignReportPrototype.copy(campaignKey = "Campaign-7", status = ExecutionStatus.SUCCESSFUL)
            val campaignReport2 =
                campaignReportPrototype.copy(campaignKey = "Campaign-8", status = ExecutionStatus.WARNING)
            val campaignReport3 =
                campaignReportPrototype.copy(campaignKey = "Campaign-9", status = ExecutionStatus.ABORTED)
            val campaignReport4 =
                campaignReportPrototype.copy(campaignKey = "Campaign-10", status = ExecutionStatus.FAILED)

            coEvery { mockNotificationPublisher["sendNotification"](any<String>(), any<CampaignReport>()) } returns Unit

            // when
            mockNotificationPublisher.publish(campaignReport1.campaignKey, campaignReport1)
            mockNotificationPublisher.publish(campaignReport2.campaignKey, campaignReport2)
            mockNotificationPublisher.publish(campaignReport3.campaignKey, campaignReport3)
            mockNotificationPublisher.publish(campaignReport4.campaignKey, campaignReport4)

            //then
            coVerifyAll {
                mockNotificationPublisher["sendNotification"](campaignReport1.campaignKey,
                    campaignReport1)
                mockNotificationPublisher["sendNotification"](campaignReport2.campaignKey,
                    campaignReport2)
                mockNotificationPublisher["sendNotification"](campaignReport3.campaignKey,
                    campaignReport3)
                mockNotificationPublisher["sendNotification"](campaignReport4.campaignKey,
                    campaignReport4)
            }
        }


    private fun composeMessage(report: CampaignReport): String {
        val duration = report.end?.let { Duration.between(report.start, it).toSeconds() }
        return """
            *Campaign*.......................................${report.campaignKey}
            *Start*.................................................${report.start}
            *End*...................................................${report.end ?: "<Running>"}
            *Duration*.........................................${duration?.let { "$it seconds" } ?: "<Running>"}
            *Started minions*............................${report.startedMinions}
            *Completed minions*.....................${report.completedMinions}
            *Successful steps executions*.....${report.successfulExecutions}
            *Failed steps executions*..............${report.failedExecutions}
            *Status*..............................................${report.status}
        """.trimIndent()
    }

    /**
     * Fetch message using the channelId and the message id
     */
    private fun retrieveMessage(channelId: String, messageId: String): Message {
        val result = spiedSlackClient.conversationsHistory { r ->
            r
                .token(slackNotificationConfiguration.token)
                .channel(channelId)
                .latest(messageId)
                .inclusive(true)
                .limit(1)
        }
        return result.get().messages[0]
    }

    private fun getColorScheme(status: ExecutionStatus): Pair<String, String> {
        return when (status) {
            ExecutionStatus.SUCCESSFUL -> Pair("#36a64f", ":large_green_circle:")
            ExecutionStatus.WARNING -> Pair("#e69d0b", ":large_orange_circle:")
            else -> Pair("#bf0606", ":red_circle:")
        }
    }
}