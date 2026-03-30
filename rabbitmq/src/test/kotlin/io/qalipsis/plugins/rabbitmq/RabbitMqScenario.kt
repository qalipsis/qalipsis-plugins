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

package io.qalipsis.plugins.rabbitmq

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.messaging.deserializer.MessageJsonDeserializer
import io.qalipsis.api.messaging.deserializer.MessageStringDeserializer
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.blackHole
import io.qalipsis.api.steps.filterNotNull
import io.qalipsis.api.steps.innerJoin
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.onEach
import io.qalipsis.plugins.rabbitmq.configuration.defaults
import io.qalipsis.plugins.rabbitmq.consumer.consume
import java.beans.ConstructorProperties

internal object RabbitMqScenario {

    internal var portContainer = 0
    internal var hostContainer = "localhost"

    internal const val minions = 2

    internal val receivedMessages = concurrentSet<String>()

    @Scenario("consumer-rabbitmq")
    fun consumeRecordsJsonDeserializer() {
        scenario {
            minionsCount = minions
            profile {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }.start().rabbitmq().consume {
            queue("user")
            connection {
                host = hostContainer
                port = portContainer
                username = "the-user"
                password = "the-password"
            }
            concurrency(2)
        }.deserialize(MessageJsonDeserializer(User::class))
            .innerJoin()
            .using { it.value.value?.id }
            .on {
                    it.rabbitmq().consume {
                        queue("user-deserializer")
                        connection {
                            host = hostContainer
                            port = portContainer
                            username = "the-user"
                            password = "the-password"
                        }
                        concurrency(2)
                    }.deserialize(MessageJsonDeserializer(User::class))
            }
            .having { it.value.value?.id }
            .filterNotNull()
            .map { joinResult -> joinResult.second.value }
            .onEach {
                receivedMessages.add(it!!.id)
            }
            .blackHole()
    }

    @Scenario("consumer-rabbitmq-string-deserializer")
    fun consumeRecordsStringDeserializer() {
        scenario {
            minionsCount = minions
            profile {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }.start().rabbitmq()
            .consume {
                queue("string-deserializer")
                connection {
                    host = hostContainer
                    port = portContainer
                    username = "the-user"
                    password = "the-password"
                }
                concurrency(2)
            }.deserialize(MessageStringDeserializer::class)
            .onEach {
                receivedMessages.add(it.value!!)
            }
    }

    @Scenario("consumer-rabbitmq-with-defaults")
    fun consumeRecordsWithDefaults() {
        val scenarioSpec = scenario {
            minionsCount = minions
            profile {
                // Starts all at once.
                regular(100, minionsCount)
            }
            rabbitmq().defaults {
                connection {
                    host = hostContainer
                    port = portContainer
                    username = "the-user"
                    password = "the-password"
                }
            }
        }
            // Register default connection for all RabbitMQ steps in this scenario.
            .start().rabbitmq()
            .consume {
                // Connection inherited from defaults.
                queue("string-deserializer-defaults")
                concurrency(2)
            }.deserialize(MessageStringDeserializer::class)
            .onEach {
                receivedMessages.add(it.value!!)
            }
    }

    data class User @ConstructorProperties("id") constructor(val id: String)
}
