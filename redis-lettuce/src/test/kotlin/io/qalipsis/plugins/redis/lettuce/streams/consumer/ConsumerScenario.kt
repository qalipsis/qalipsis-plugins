package io.qalipsis.plugins.redis.lettuce.streams.consumer

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.rampup.regular
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
