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

package io.qalipsis.plugins.rabbitmq.configuration

import javax.validation.constraints.NotBlank
import javax.validation.constraints.Positive

/**
 * Connection configuration for a RabbitMQ broker.
 *
 * @property username for authenticate the connection, defaults to "guest".
 * @property password for authenticate the connection, defaults to "guest".
 * @property virtualHost of the connection, defaults to "/".
 * @property host of the broker, defaults to "localhost".
 * @property port of the connection, defaults to "5672".
 * @property clientProperties properties used in the connection factory, defaults to an empty map.
 *
 * @author Gabriel Moraes
 */
data class RabbitMqConnectionConfiguration internal constructor(
    @field:NotBlank var username: String = "guest",
    @field:NotBlank var password: String = "guest",
    @field:NotBlank var virtualHost: String = "/",
    @field:NotBlank var host: String = "localhost",
    @field:Positive var port: Int = 5672,
    var clientProperties: Map<String, Any> = emptyMap()
)
