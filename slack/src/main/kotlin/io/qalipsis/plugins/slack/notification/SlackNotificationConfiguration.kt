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