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

package io.qalipsis.plugins.jakarta.consumer

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.blackHole
import io.qalipsis.api.steps.filterNotNull
import io.qalipsis.api.steps.innerJoin
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.onEach
import io.qalipsis.plugins.jakarta.deserializer.JakartaJsonDeserializer
import io.qalipsis.plugins.jakarta.deserializer.JakartaStringDeserializer
import io.qalipsis.plugins.jakarta.jakarta
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
import java.beans.ConstructorProperties
import java.util.concurrent.LinkedBlockingDeque

internal object JakartaScenario {

    internal lateinit var queueConnectionFactory : ActiveMQConnectionFactory

    private const val minions = 2

    internal val receivedMessages = LinkedBlockingDeque<String>(10)

    @Scenario("consumer-jakarta")
    fun consumeRecords() {
        scenario {
            minionsCount = minions
            profile {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }.start().jakarta()
            .consume {
                queues("queue-1")
                queueConnection { queueConnectionFactory.createQueueConnection() }
            }.deserialize(JakartaJsonDeserializer(User::class))
            .innerJoin()
            .using { it.value.record.value.id }
            .on {
                    it.jakarta()
                        .consume {
                            queues("queue-2")
                            queueConnection { queueConnectionFactory.createQueueConnection() }
                        }.deserialize(JakartaJsonDeserializer(User::class))
            }
            .having { it.value.record.value.id }
            .filterNotNull()
            .map { joinResult -> joinResult.second.record }
            .onEach {
                receivedMessages.add(it.value.id)
            }
            .blackHole()
    }

    @Scenario("consumer-jakarta-string-deserializer")
    fun consumeRecordsStringDeserializer() {
        scenario {
            minionsCount = minions
            profile {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }.start().jakarta()
            .consume {
                queues("queue-3")
                queueConnection { queueConnectionFactory.createQueueConnection() }
            }.deserialize(JakartaStringDeserializer::class)
            .onEach {
                receivedMessages.add(it.record.value)
            }
            .blackHole()
    }

    data class User @ConstructorProperties("id", "name") constructor(val id: String, val name: String)
}
