package io.qalipsis.plugins.redis.lettuce.save

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.rampup.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.plugins.redis.lettuce.redisLettuce
import io.qalipsis.plugins.redis.lettuce.save.records.HashRecord
import io.qalipsis.plugins.redis.lettuce.save.records.ValueRecord
import io.qalipsis.plugins.redis.lettuce.streams.consumer.streamsConsume

/**
 * @author Gabriel Moraes
 */
object SaveScenario {

    private const val minions = 30

    var dbNodes = listOf<String>()
    var dbDatabase = 0

    @Scenario
    fun producerData() {
        scenario("lettuce-save-record") {
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

                concurrency(1)
                group("consumer-test")
                streamKey("test")
                monitoring {
                    events = false
                    meters = false
                }
            }.flatten()
            .redisLettuce().save {
                name = "save"
                connection {
                    nodes = dbNodes
                    database = dbDatabase
                }
                records { _, _ ->
                    listOf(
                        ValueRecord(
                            key = "value",
                            value = "test"
                        ),
                        HashRecord(
                            key = "hash",
                            value = mapOf("test" to "test1")
                        )
                    )
                }
                monitoring {
                    events = true
                    meters = true
                }
            }
    }

}