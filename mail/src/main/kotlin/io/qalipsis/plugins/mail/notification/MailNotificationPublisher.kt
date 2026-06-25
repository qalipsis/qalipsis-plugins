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

package io.qalipsis.plugins.mail.notification

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.CampaignReportPublisher
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.plugins.mail.notification.MailNotificationPublisher.Companion.CHARSET
import io.qalipsis.plugins.mail.notification.MailNotificationPublisher.Companion.CONTENT_TYPE
import io.qalipsis.plugins.mail.notification.MailNotificationPublisher.Companion.TRANSPORT_PROTOCOL
import io.qalipsis.plugins.mail.notification.MailNotificationPublisher.Companion.logger
import jakarta.inject.Singleton
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart


/**
 * Custom implementation of a [CampaignReportPublisher] that sends a mail notification
 * at the end of a campaign.
 *
 * @author Francisca Eze
 */
@Singleton
@Requirements(
    Requires(beans = [MailNotificationConfiguration::class])
)
internal class MailNotificationPublisher(
    val mailConfiguration: MailNotificationConfiguration,
    @Value("\${report.export.html.folder}") private val reportFolder: String,
) : CampaignReportPublisher {

    private val properties = Properties()

    override suspend fun publish(tenant: String, campaignKey: CampaignKey, report: CampaignReport) {
        val reportStatus = ReportExecutionStatus.values().firstOrNull { it.name == report.status.toString() }
        if (reportStatus != null && ((mailConfiguration.status.contains(ReportExecutionStatus.ALL)) || mailConfiguration.status.contains(
                ReportExecutionStatus.valueOf(reportStatus.toString())
            ))
        ) {
            sendNotification(report)
        }
    }

    private fun sendNotification(report: CampaignReport) {
        setMailProperties()
        val session = getSession(properties)
        val message = MimeMessage(session)
        val attachmentFile = File.createTempFile(report.campaignKey, ".zip")
        try {
            message.apply {
                subject = "${report.campaignKey} ${report.status}"
                setFrom(InternetAddress(mailConfiguration.from))
                setRecipients(Message.RecipientType.TO, mailConfiguration.to.map { InternetAddress(it) }.toTypedArray())
                if (!mailConfiguration.cc.isNullOrEmpty()) {
                    setRecipients(
                        Message.RecipientType.CC,
                        mailConfiguration.cc!!.map { InternetAddress(it) }.toTypedArray()
                    )
                }
            }
            val textPart = MimeBodyPart()
            val multipart: Multipart = MimeMultipart()
            textPart.setText(composeMessageBody(report), CHARSET, CONTENT_TYPE)
            multipart.addBodyPart(textPart)

            // Add the HTML report as attachment if it exists.
            val htmlReport = File("$reportFolder/${report.campaignKey}.html")
            if (mailConfiguration.html && reportFolder.isNotEmpty() && htmlReport.canRead()) {
                val attachmentPart = MimeBodyPart()
                MailUtils.compressSFile(htmlReport, attachmentFile)
                attachmentPart.dataHandler = DataHandler(FileDataSource(attachmentFile))
                attachmentPart.fileName = attachmentFile.name
                multipart.addBodyPart(attachmentPart)
            }
            message.setContent(multipart)
            Transport.send(message)
            logger.info { "Mail sent successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Was not able to send mail: ${e.message}" }
            throw e
        } finally {
            if (attachmentFile.exists()) {
                val deleteSuccess = attachmentFile.delete()
                logger.debug { "Deletion of the temporary ZIP report archive $attachmentFile: $deleteSuccess" }
            }
        }
    }

    internal fun composeMessageBody(report: CampaignReport): String {
        val colors = statusColorScheme(report.status)

        val failureRate = failureRate(report.successfulExecutions, report.failedExecutions)
        val failRateColor = if (failureRate > 0) "#c0392b" else "#27ae60"
        val dateRange = formatDateRange(report.start, report.end)
        val duration = formatDuration(report.start, report.end)

        val scenariosSection = if (report.scenariosReports.isNotEmpty()) {
            val cards = report.scenariosReports.joinToString("\n") { scenario ->
                val sc = statusColorScheme(scenario.status)
                val scRate = failureRate(scenario.successfulExecutions, scenario.failedExecutions)
                val scRateColor = if (scRate > 0) "#c0392b" else "#27ae60"
                """        <div style="margin-bottom:6px;border-left:3px solid ${sc.color};background:${sc.headerBg};border-radius:0 3px 3px 0;padding:6px 10px">
          <div style="margin-bottom:3px">
            <span style="font-size:12px;color:#1d1c1d">${scenario.scenarioName}</span>
            <span style="margin-left:8px;padding:1px 8px;background:#f0f0f0;color:${sc.color};border-radius:20px;font-size:10px;font-weight:700;font-family:'Courier New',Courier,monospace">${scenario.status}</span>
          </div>
          <div style="font-size:11px;color:#555">&#9989; <b style="color:#27ae60">${scenario.successfulExecutions ?: 0}</b>&nbsp;&nbsp;&#10060; <b style="color:#c0392b">${scenario.failedExecutions ?: 0}</b>&nbsp;&nbsp;<span style="color:${scRateColor};font-weight:700">${scRate}% failure</span></div>
        </div>"""
            }
            """
      <div style="padding:10px 16px;border-top:1px solid #f0f0f0">
        <div style="font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:1px;color:#aaa;margin-bottom:8px">Scenarios</div>
$cards
      </div>"""
        } else {
            ""
        }

        return """<body style="margin:0;padding:0;background:#f4f4f4;font-family:Arial,Helvetica,sans-serif">
  <div style="max-width:600px;margin:20px auto">
    <div style="border-left:4px solid ${colors.color};background:#fff;border-radius:0 4px 4px 0;overflow:hidden">
      <div style="padding:12px 16px;background:${colors.headerBg}">
        <span style="font-size:16px;font-weight:700;color:#1d1c1d">${report.campaignKey}</span>
        <span style="margin-left:10px;padding:2px 10px;background:${colors.badgeBg};color:${colors.badgeFg};border-radius:20px;font-size:11px;font-weight:700;border:1px solid ${colors.borderColor};font-family:Arial,sans-serif">${report.status}</span>
      </div>
      <div style="padding:7px 16px;font-size:12px;color:#777;border-bottom:1px solid #f0f0f0">
        ${dateRange}  &middot;  ${duration}
      </div>
      <table style="width:100%;border-collapse:collapse">
        <tr>
          <td style="padding:10px 16px;width:50%;vertical-align:top;border-bottom:1px solid #f0f0f0">
            <div style="font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:1px;color:#aaa;margin-bottom:5px">Minions</div>
            <div style="font-size:13px;color:#444">&#8593; <b>${report.startedMinions}</b> started &nbsp; &#10003; <b>${report.completedMinions}</b> done</div>
          </td>
          <td style="padding:10px 16px;width:50%;vertical-align:top;border-bottom:1px solid #f0f0f0;border-left:1px solid #f0f0f0">
            <div style="font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:1px;color:#aaa;margin-bottom:5px">Executions</div>
            <div style="font-size:13px;color:#444">&#9989; <b style="color:#27ae60">${report.successfulExecutions}</b> &nbsp; &#10060; <b style="color:#c0392b">${report.failedExecutions}</b></div>
            <div style="font-size:11px;font-weight:700;color:${failRateColor};margin-top:3px">${failureRate}% failure rate</div>
          </td>
        </tr>
      </table>${scenariosSection}
    </div>
  </div>
</body>"""
    }

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

    private data class StatusColorScheme(
        val color: String,
        val headerBg: String,
        val badgeBg: String,
        val badgeFg: String,
        val borderColor: String,
    )

    private fun statusColorScheme(status: ExecutionStatus) = when (status) {
        ExecutionStatus.SUCCESSFUL -> StatusColorScheme("#27ae60", "#f0faf5", "#d4efdf", "#1e8449", "#a9dfbf")
        ExecutionStatus.WARNING -> StatusColorScheme("#e67e22", "#fdf6ee", "#fde9cc", "#b7570f", "#f5cba7")
        else -> StatusColorScheme("#c0392b", "#fdf2f1", "#f9d2cf", "#922b21", "#f1948a")
    }

    /**
     * Returns key, value [Properties] pair to be used for mail session configuration.
     */
    private fun setMailProperties() {
        properties["mail.smtp.host"] = mailConfiguration.host
        properties["mail.smtp.port"] = mailConfiguration.port
        properties["mail.smtp.auth"] = mailConfiguration.authenticationMode == AuthenticationMode.USERNAME_PASSWORD
        properties["mail.smtp.ssl.enable"] = mailConfiguration.ssl
        properties["mail.smtp.starttls.enable"] = mailConfiguration.starttls
        properties["mail.transport.protocol"] = TRANSPORT_PROTOCOL
    }

    /**
     * Returns the default or a new mail session based on the configured properties and [AuthenticationMode].
     *
     * @param properties persistent set of properties to configure mail session
     */
    private fun getSession(properties: Properties): Session {
        return if (mailConfiguration.authenticationMode == AuthenticationMode.USERNAME_PASSWORD) {
            Session.getInstance(properties, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(mailConfiguration.username, mailConfiguration.password)
                }
            })
        } else {
            Session.getDefaultInstance(properties, null)
        }
    }

    /**
     * @property TRANSPORT_PROTOCOL specifies the protocol type to be used in configuring mail properties
     * @property logger to log mail publishing events
     * @property CHARSET character set used in encoding mail properties
     * @property CONTENT_TYPE specifies the content type to be used by [MimeBodyPart]
     */
    companion object {
        private const val TRANSPORT_PROTOCOL = "smtp"
        private const val CONTENT_TYPE = "html"
        private const val CHARSET = "utf-8"
        private val logger = logger()
    }
}
