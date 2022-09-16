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
