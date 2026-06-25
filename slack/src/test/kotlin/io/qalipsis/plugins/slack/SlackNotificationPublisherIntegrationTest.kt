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

package io.qalipsis.plugins.slack

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import assertk.assertions.prop
import com.slack.api.methods.AsyncMethodsClient
import com.slack.api.model.Attachment
import com.slack.api.model.Message
import com.slack.api.model.block.SectionBlock
import com.slack.api.model.block.composition.MarkdownTextObject
import io.micronaut.context.annotation.Value
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.coExcludeRecords
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ScenarioReport
import io.qalipsis.api.sync.asSuspended
import io.qalipsis.plugins.slack.notification.ReportExecutionStatus
import io.qalipsis.plugins.slack.notification.SlackNotificationConfiguration
import io.qalipsis.plugins.slack.notification.SlackNotificationPublisher
import io.qalipsis.plugins.slack.notification.catadioptre.asyncSlackMethodsClient
import io.qalipsis.plugins.slack.notification.catadioptre.postChatMessageRequest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import java.time.Instant
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

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

    private lateinit var spiedNotificationPublisher: SlackNotificationPublisher

    @BeforeEach
    internal fun setupAll() {
        log.info { "Bot Token: ${botToken.take(3)}...${botToken.takeLast(3)}" }
        campaignReportPrototype = CampaignReport(
            campaignKey = "Campaign-1",
            startedMinions = 1000,
            completedMinions = 990,
            successfulExecutions = 990,
            failedExecutions = 10,
            status = ExecutionStatus.SUCCESSFUL,
            scheduledMinions = 4,
            start = Instant.parse("2022-10-29T00:00:00.00Z"),
            end = Instant.parse("2022-11-05T00:00:00.00Z"),
            scenariosReports = listOf(
                ScenarioReport(
                    campaignKey = "Campaign-1",
                    scenarioName = "scenario-login-flow",
                    start = Instant.parse("2022-10-29T00:00:00.00Z"),
                    end = Instant.parse("2022-11-05T00:00:00.00Z"),
                    startedMinions = 600,
                    completedMinions = 600,
                    successfulExecutions = 600,
                    failedExecutions = 0,
                    status = ExecutionStatus.SUCCESSFUL,
                    messages = emptyList()
                ),
                ScenarioReport(
                    campaignKey = "Campaign-1",
                    scenarioName = "scenario-checkout-flow",
                    start = Instant.parse("2022-10-29T00:00:00.00Z"),
                    end = Instant.parse("2022-11-05T00:00:00.00Z"),
                    startedMinions = 400,
                    completedMinions = 390,
                    successfulExecutions = 390,
                    failedExecutions = 10,
                    status = ExecutionStatus.FAILED,
                    messages = emptyList()
                )
            )
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
        spiedNotificationPublisher =
            spyk(SlackNotificationPublisher(slackNotificationConfiguration), recordPrivateCalls = true)

        coExcludeRecords { spiedNotificationPublisher.configuration }
        coExcludeRecords { spiedNotificationPublisher.publish(RandomStringUtils.randomAlphanumeric(6), any(), any()) }
    }

    @Test
    fun `should send notification for successful campaign with appropriate color scheme`() =
        testDispatcherProvider.run {
            // given
            val campaignReport = campaignReportPrototype.copy(failedExecutions = 0)

            // when
            val response = slackNotificationPublisher.postChatMessageRequest(campaignReport).asSuspended().get()

            // then
            assertThat(response.isOk).isTrue()
            val retrievedMessage: Message = retrieveMessage(response.channel, response.ts)
            val attachment = retrievedMessage.attachments[0]
            assertThat(attachment).prop(Attachment::getColor).isEqualTo("#36a64f")
            assertThat(attachment).prop(Attachment::getFallback)
                .isEqualTo("${campaignReport.campaignKey} ${campaignReport.status}")
            val titleBlock = attachment.blocks[0] as SectionBlock
            assertThat((titleBlock.text as MarkdownTextObject).text).contains(campaignReport.campaignKey)
            assertThat((titleBlock.text as MarkdownTextObject).text).contains(campaignReport.status.name)
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

        // when
        val response = slackNotificationPublisher.postChatMessageRequest(campaignReport).asSuspended().get()

        // then
        assertThat(response.isOk).isTrue()
        val retrievedMessage: Message = retrieveMessage(response.channel, response.ts)
        val attachment = retrievedMessage.attachments[0]
        assertThat(attachment).prop(Attachment::getColor).isEqualTo("#bf0606")
        assertThat(attachment).prop(Attachment::getFallback)
            .isEqualTo("${campaignReport.campaignKey} ${campaignReport.status}")
        val titleBlock = attachment.blocks[0] as SectionBlock
        assertThat((titleBlock.text as MarkdownTextObject).text).contains(campaignReport.campaignKey)
        assertThat((titleBlock.text as MarkdownTextObject).text).contains(campaignReport.status.name)
    }

    @Test
    fun `should send notification for a campaign with warning status and with appropriate color scheme`() =
        testDispatcherProvider.run {
            // given
            val campaignReport = campaignReportPrototype.copy(
                status = ExecutionStatus.WARNING,
                failedExecutions = 500,
                successfulExecutions = 200,
                completedMinions = 200,
                startedMinions = 700,
                campaignKey = "Campaign-3"
            )

            // when
            val response = slackNotificationPublisher.postChatMessageRequest(campaignReport).asSuspended().get()

            // then
            assertThat(response.isOk).isTrue()
            val retrievedMessage: Message = retrieveMessage(response.channel, response.ts)
            val attachment = retrievedMessage.attachments[0]
            assertThat(attachment).prop(Attachment::getColor).isEqualTo("#e69d0b")
            assertThat(attachment).prop(Attachment::getFallback)
                .isEqualTo("${campaignReport.campaignKey} ${campaignReport.status}")
            val titleBlock = attachment.blocks[0] as SectionBlock
            assertThat((titleBlock.text as MarkdownTextObject).text).contains(campaignReport.campaignKey)
            assertThat((titleBlock.text as MarkdownTextObject).text).contains(campaignReport.status.name)
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
            coEvery {
                spiedNotificationPublisher["sendNotification"](
                    any<String>(),
                    any<CampaignReport>()
                )
            } returns Unit

            // when
            spiedNotificationPublisher.publish(
                RandomStringUtils.randomAlphanumeric(6),
                campaignReport.campaignKey,
                campaignReport
            )

            //then
            coVerifyNever { spiedNotificationPublisher["sendNotification"](campaignReport.campaignKey, campaignReport) }
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
            coEvery {
                spiedNotificationPublisher["sendNotification"](
                    any<String>(),
                    any<CampaignReport>()
                )
            } returns Unit

            // when
            spiedNotificationPublisher.publish(
                RandomStringUtils.randomAlphanumeric(6),
                campaignReport.campaignKey,
                campaignReport
            )

            //then
            coVerifyNever { spiedNotificationPublisher["sendNotification"](campaignReport.campaignKey, campaignReport) }
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
            coEvery {
                spiedNotificationPublisher["sendNotification"](
                    any<String>(),
                    any<CampaignReport>()
                )
            } returns Unit

            // when
            spiedNotificationPublisher.publish(
                RandomStringUtils.randomAlphanumeric(6),
                campaignReport.campaignKey,
                campaignReport
            )

            //then
            coVerifyOnce { spiedNotificationPublisher["sendNotification"](campaignReport.campaignKey, campaignReport) }
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

    private companion object {
        val log = logger()
    }
}
