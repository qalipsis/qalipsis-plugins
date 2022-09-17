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
