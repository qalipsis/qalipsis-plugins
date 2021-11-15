package io.qalipsis.plugins.rabbitmq

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.messaging.deserializer.MessageJsonDeserializer
import io.qalipsis.api.messaging.deserializer.MessageStringDeserializer
import io.qalipsis.api.rampup.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.blackHole
import io.qalipsis.api.steps.filterNotNull
import io.qalipsis.api.steps.innerJoin
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.onEach
import io.qalipsis.plugins.rabbitmq.consumer.consume
import java.beans.ConstructorProperties

internal object RabbitMqScenario {

    internal var portContainer = 0
    internal var hostContainer = "localhost"

    internal const val minions = 2

    internal val receivedMessages = concurrentSet<String>()

    @Scenario
    fun consumeRecordsJsonDeserializer() {
        scenario("consumer-rabbitmq") {
            minionsCount = minions
            rampUp {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }.start().rabbitmq().consume {
            queue("user")
            connection {
                host = hostContainer
                port = portContainer
                password = "defaultpass"
                username = "user"
            }
            concurrency(2)
        }.deserialize(MessageJsonDeserializer(User::class))
            .innerJoin(
                using = { it.value.value?.id },
                on = {
                    it.rabbitmq().consume {
                        queue("user-deserializer")
                        connection {
                            host = hostContainer
                            port = portContainer
                            password = "defaultpass"
                            username = "user"
                        }
                        concurrency(2)
                    }.deserialize(MessageJsonDeserializer(User::class))
                },
                having = { it.value.value?.id }
            )
            .filterNotNull()
            .map { joinResult -> joinResult.second.value }
            .onEach {
                receivedMessages.add(it!!.id)
            }
            .blackHole()
    }

    @Scenario
    fun consumeRecordsStringDeserializer() {
        scenario("consumer-rabbitmq-string-deserializer") {
            minionsCount = minions
            rampUp {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }.start().rabbitmq()
            .consume {
                queue("string-deserializer")
                connection {
                    host = hostContainer
                    port = portContainer
                    password = "defaultpass"
                    username = "user"
                }
                concurrency(2)
            }.deserialize(MessageStringDeserializer::class)
            .onEach {
                receivedMessages.add(it.value!!)
            }
    }

    data class User @ConstructorProperties("id") constructor(val id: String)
}
