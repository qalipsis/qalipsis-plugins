package io.qalipsis.plugins.slack.notification

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.micronaut.core.bind.annotation.Bindable
import io.micronaut.core.util.StringUtils
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull

/**
 * Configuration for [SlackNotificationPublisher].
 *
 * @author Francisca Eze
 */
@Requires(property = "report.export.slack.enabled", value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
@ConfigurationProperties("report.export.slack")
interface SlackNotificationConfiguration {

    @get:Bindable(defaultValue = "false")
    @get:NotNull
    val enabled: Boolean

    @get:NotNull
    val channel: String

    @get:Bindable(defaultValue = "ALL")
    @get:NotEmpty
    val status: Set<ReportExecutionStatus>

    @get:Bindable(defaultValue = "https://slack.com/api/chat.postMessage")
    @get:NotBlank
    val url: String

    @get:NotBlank
    val token: String

}