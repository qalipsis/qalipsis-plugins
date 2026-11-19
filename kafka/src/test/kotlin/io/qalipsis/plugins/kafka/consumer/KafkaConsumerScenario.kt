/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.plugins.kafka.consumer

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.immediate
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.blackHole
import io.qalipsis.api.steps.filterNotNull
import io.qalipsis.api.steps.innerJoin
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.onEach
import io.qalipsis.plugins.kafka.configuration.defaults
import io.qalipsis.plugins.kafka.kafka
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.serialization.Serdes

/**
 *
 * @author Eric Jessé
 */
internal object KafkaConsumerScenario {

    const val minions = 20

    const val topicLeft = "left"

    const val topicRight = "right"

    const val topicDefaults = "defaults"

    val receivedMessages = concurrentSet<String>()

    internal var bootstrap: String = ""

    private val intDeserializer = Serdes.Integer().deserializer()

    private val stringDeserializer = Serdes.String().deserializer()

    @Scenario("consumer-kafka")
    fun consumeRecordsAndCorrelate() {
        scenario {
            minionsCount = minions
            profile {
                // Starts all at once.
                immediate()
            }
        }
            .start()
            .kafka()
            .consume {
                name = "left-kafka-consumer"
                bootstrap(bootstrap)
                topics(topicLeft)
                groupId("kafka-left")
                pollTimeout(100)
                offsetReset(OffsetResetStrategy.EARLIEST)
            }.flatten(intDeserializer, stringDeserializer)
            .innerJoin()
            .using { it.value.record.key }
            .on {
                    it.kafka()
                        .consume {
                            name = "right-kafka-consumer"
                            bootstrap(bootstrap)
                            topics(topicRight)
                            groupId("kafka-right")
                            pollTimeout(100)
                            offsetReset(OffsetResetStrategy.EARLIEST)
                        }.flatten(intDeserializer, stringDeserializer)
            }
            .having { it.value.record.key }
            .map { joinResult -> joinResult?.first?.let { "${it.record.value} - ${joinResult.second.record.value}" } }
            .filterNotNull()
            .onEach { receivedMessages.add(it) }
            .blackHole()
    }

    @Scenario("consumer-kafka-with-defaults")
    fun consumeRecordsWithDefaults() {
        scenario {
            minionsCount = minions
            profile {
                immediate()
            }
            kafka().defaults {
                bootstrap(bootstrap)
            }
        }
            .start()
            .kafka()
            .consume {
                name = "kafka-consumer-defaults"
                // bootstrap inherited from defaults
                topics(topicDefaults)
                groupId("kafka-defaults")
                pollTimeout(100)
                offsetReset(OffsetResetStrategy.EARLIEST)
            }.flatten(intDeserializer, stringDeserializer)
            .onEach { receivedMessages.add("${it.record.key} - ${it.record.value}") }
            .blackHole()
    }
}