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

package io.qalipsis.plugins.redis.lettuce.streams.producer

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.logErrors
import io.qalipsis.plugins.redis.lettuce.redisLettuce
import io.qalipsis.plugins.redis.lettuce.streams.consumer.streamsConsume

/**
 * @author Gabriel Moraes
 */
object ProducerScenario {

    const val minions = 30

    var dbNodes = listOf<String>()
    var dbDatabase = 0

    @Scenario("lettuce-producer")
    fun producerData() {
        scenario {
            minionsCount = minions
            profile {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }
            .start()
            .redisLettuce()
            .streamsConsume {
                name = "consumer"
                connection {
                    nodes = dbNodes
                    database = dbDatabase
                }

                concurrency(5)
                group("consumer-test")
                streamKey("test")
                monitoring {
                    events = false
                    meters = false
                }
            }.flatten()
            .redisLettuce().streamsProduce {
                name = "producer"
                connection {
                    nodes = dbNodes
                    database = dbDatabase
                }
                records { _, input ->
                    listOf(
                        LettuceStreamsProduceRecord(
                            key = "producer.test",
                            value = mapOf("test" to input.value.values.first())
                        )
                    )
                }
                monitoring {
                    events = true
                    meters = true
                }
            }
            .logErrors()
    }

}
