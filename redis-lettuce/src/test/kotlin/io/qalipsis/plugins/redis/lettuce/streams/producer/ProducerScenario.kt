package io.qalipsis.plugins.redis.lettuce.streams.producer

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.rampup.regular
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

    @Scenario
    fun producerData() {
        scenario("lettuce-producer") {
            minionsCount = minions
            rampUp {
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
