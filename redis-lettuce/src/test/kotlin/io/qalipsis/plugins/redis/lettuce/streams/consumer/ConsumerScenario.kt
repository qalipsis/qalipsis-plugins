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

    @Scenario
    fun consumerDataCluster() {
        scenario("lettuce-consumer-cluster") {
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


    @Scenario
    fun consumerData() {
        scenario("lettuce-consumer") {
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
