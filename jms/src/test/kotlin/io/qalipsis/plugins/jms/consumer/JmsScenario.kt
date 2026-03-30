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

package io.qalipsis.plugins.jms.consumer

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.blackHole
import io.qalipsis.api.steps.filterNotNull
import io.qalipsis.api.steps.innerJoin
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.onEach
import io.qalipsis.plugins.jms.deserializer.JmsJsonDeserializer
import io.qalipsis.plugins.jms.deserializer.JmsStringDeserializer
import io.qalipsis.plugins.jms.jms
import org.apache.activemq.ActiveMQConnectionFactory
import java.beans.ConstructorProperties
import java.util.concurrent.LinkedBlockingDeque

internal object JmsScenario {

    internal var queueConnection = ActiveMQConnectionFactory().createQueueConnection()

    internal const val minions = 2

    internal val receivedMessages = LinkedBlockingDeque<String>(10)

    @Scenario("consumer-jms")
    fun consumeRecords() {
        scenario {
            minionsCount = minions
            profile {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }.start().jms()
            .consume {
                queues("queue-1")
                queueConnection { queueConnection }
            }.deserialize(JmsJsonDeserializer(User::class))
            .innerJoin()
            .using { it.value.record.value.id }
            .on {
                    it.jms()
                        .consume {
                            queues("queue-2")
                            queueConnection { queueConnection }
                        }.deserialize(JmsJsonDeserializer(User::class))
            }
            .having { it.value.record.value.id }
            .filterNotNull()
            .map { joinResult -> joinResult.second.record }
            .onEach {
                receivedMessages.add(it.value.id)
            }
            .blackHole()
    }

    @Scenario("consumer-jms-string-deserializer")
    fun consumeRecordsStringDeserializer() {
        scenario {
            minionsCount = minions
            profile {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }.start().jms()
            .consume {
                queues("queue-3")
                queueConnection { queueConnection }
            }.deserialize(JmsStringDeserializer::class)
            .onEach {
                receivedMessages.add(it.record.value)
            }
            .blackHole()
    }

    data class User @ConstructorProperties("id", "name") constructor(val id: String, val name: String)
}
