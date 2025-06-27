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

package io.qalipsis.plugins.redis.lettuce.streams.consumer

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.logErrors
import io.qalipsis.api.steps.onEach
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionType
import io.qalipsis.plugins.redis.lettuce.redisLettuce

/**
 * @author Gabriel Moraes
 */
object ConsumerScenario {

    const val minions = 30

    val receivedMessages = concurrentSet<String>()
    var dbNodes = listOf<String>()
    var dbDatabase = 0

    @Scenario("lettuce-consumer-cluster")
    fun consumerDataCluster() {
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
                    redisConnectionType = RedisConnectionType.CLUSTER
                }

                concurrency(5)
                group("consumer-test-1")
                streamKey("test")
                monitoring {
                    events = false
                    meters = false
                }
            }.flatten()
            .logErrors()
            .onEach { receivedMessages.add(it.value.values.first()) }
    }


    @Scenario("lettuce-consumer")
    fun consumerData() {
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
                group("consumer-test-2")
                streamKey("test")
                monitoring {
                    events = false
                    meters = false
                }
            }.flatten()
            .logErrors()
            .onEach { receivedMessages.add(it.value.values.first()) }
    }

}
