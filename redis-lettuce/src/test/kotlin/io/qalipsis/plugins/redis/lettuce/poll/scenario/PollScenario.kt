package io.qalipsis.plugins.redis.lettuce.poll.scenario

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.rampup.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.innerJoin
import io.qalipsis.api.steps.logErrors
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.onEach
import io.qalipsis.plugins.redis.lettuce.AbstractRedisIntegrationTest.Companion.REDIS_PASS
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionType
import io.qalipsis.plugins.redis.lettuce.poll.pollHscan
import io.qalipsis.plugins.redis.lettuce.poll.pollScan
import io.qalipsis.plugins.redis.lettuce.poll.pollSscan
import io.qalipsis.plugins.redis.lettuce.poll.pollZscan
import io.qalipsis.plugins.redis.lettuce.redisLettuce
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 *
 * Scenario to demo how the poll step can work. The scenario reads the entries in a building on one side and the exits
 * on the other side.
 *
 * Records related to the same person are joined and the duration is then printed out in the console.
 *
 * @author Gabriel Moraes
 */
object PollScenario {

    const val pollScanMinions = 3
    const val pollSScanMinions = 3
    const val pollZScanMinions = 3
    const val pollHScanMinions = 2
    const val pollScanClusterMinions = 10

    val receivedMessages = concurrentSet<String>()

    fun resetReceivedMessages() = receivedMessages.clear()

    var dbNodes = listOf<String>()
    var dbDatabase = 0

    @Scenario
    fun pollData() {
        scenario("lettuce-poll-sscan") {
            minionsCount = pollSScanMinions
            rampUp {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }
            .start()
            .redisLettuce()
            .pollSscan {
                name = "poll.sscan"
                connection {
                    nodes = dbNodes
                    database = dbDatabase
                    authPassword = REDIS_PASS
                }

                keyOrPattern("test")

                pollDelay(Duration.ofSeconds(1))
                monitoring {
                    events = false
                    meters = false
                }
            }.flatten()
            .logErrors()
            .map { UserEvent(it.value, LocalDateTime.ofEpochSecond(it.recordTimestamp, 0, ZoneOffset.UTC)) }
            .innerJoin(
                using = { it.value.username },
                on = {
                    it.redisLettuce()
                        .pollSscan {
                            name = "poll.sscan.out"
                            connection {
                                nodes = dbNodes
                                database = dbDatabase
                                authPassword = REDIS_PASS
                            }
                            keyOrPattern("testout")
                            pollDelay(Duration.ofSeconds(1))
                            monitoring {
                                events = false
                                meters = false
                            }
                        }
                        .flatten()
                        .map { UserEvent(it.value, LocalDateTime.ofEpochSecond(it.recordTimestamp, 0, ZoneOffset.UTC)) }
                },
                having = { it.value.username }
            )
            .logErrors()
            .onEach { receivedMessages.add(it!!.second.username) }
            .onEach { println(it) }
    }

    @Scenario
    fun pollDataScan() {
        scenario("lettuce-poll-scan") {
            minionsCount = pollScanMinions
            rampUp {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }
            .start()
            .redisLettuce()
            .pollScan {
                name = "poll.scan"
                connection {
                    nodes = dbNodes
                    database = dbDatabase
                    authPassword = REDIS_PASS
                }

                keyOrPattern("scan-t*")

                pollDelay(Duration.ofSeconds(1))

                monitoring {
                    events = false
                    meters = false
                }
            }.flatten()
            .logErrors()
            .onEach { receivedMessages.add(it.value) }
            .onEach { println(it) }
    }

    @Scenario
    fun pollDataHScan() {
        scenario("lettuce-poll-hscan") {
            minionsCount = pollHScanMinions
            rampUp {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }
            .start()
            .redisLettuce()
            .pollHscan {
                name = "poll.hscan"
                connection {
                    nodes = dbNodes
                    database = dbDatabase
                    authPassword = REDIS_PASS
                }

                keyOrPattern("hscan-test")

                pollDelay(Duration.ofSeconds(1))

                monitoring {
                    events = false
                    meters = false
                }
            }.flatten()
            .logErrors()
            .map { "${it.value.first} ${it.value.second}" }
            .onEach { receivedMessages.add(it) }
            .onEach { println(it) }
    }


    @Scenario
    fun pollDataZScan() {
        scenario("lettuce-poll-zscan") {
            minionsCount = pollZScanMinions
            rampUp {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }
            .start()
            .redisLettuce()
            .pollZscan {
                name = "poll.zscan"
                connection {
                    nodes = dbNodes
                    database = dbDatabase
                    authPassword = REDIS_PASS
                }

                keyOrPattern("zscan-test")

                pollDelay(Duration.ofSeconds(1))
                monitoring {
                    events = false
                    meters = false
                }
            }.flatten()
            .logErrors()
            .map { "${it.value.first} ${it.value.second}" }
            .onEach { receivedMessages.add(it) }
            .onEach { println(it) }
    }


    @Scenario
    fun pollDataHScanAcl() {
        scenario("lettuce-poll-hscan-with-acl") {
            minionsCount = pollHScanMinions
            rampUp {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }
            .start()
            .redisLettuce()
            .pollHscan {
                name = "poll.hscanacl"
                connection {
                    nodes = dbNodes
                    database = dbDatabase
                    authPassword = "dszcZT"
                    authUser = "alice"
                }

                keyOrPattern("hscan-test-acl")

                pollDelay(Duration.ofSeconds(1))

                monitoring {
                    events = false
                    meters = false
                }
            }.flatten()
            .logErrors()
            .map { "${it.value.first} ${it.value.second}" }
            .onEach { receivedMessages.add(it) }
            .onEach { println(it) }
    }

    @Scenario
    fun pollDataScanCluster() {
        scenario("lettuce-poll-scan-cluster") {
            minionsCount = pollScanClusterMinions
            rampUp {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }
            .start()
            .redisLettuce()
            .pollScan {
                name = "poll.scan.cluster"
                connection {
                    nodes = dbNodes
                    redisConnectionType = RedisConnectionType.CLUSTER
                }

                keyOrPattern("*")

                pollDelay(Duration.ofSeconds(1))

                monitoring {
                    events = true
                    meters = true
                }
            }.flatten()
            .logErrors()
            .onEach { receivedMessages.add(it.value) }
            .onEach { println(it) }
    }

    @Scenario
    fun pollDataScanClusterWithoutFlatten() {
        scenario("lettuce-poll-scan-cluster-batch") {
            minionsCount = 2
            rampUp {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }
            .start()
            .redisLettuce()
            .pollScan {
                name = "poll.scan.cluster"
                connection {
                    nodes = dbNodes
                    database = dbDatabase
                    redisConnectionType = RedisConnectionType.CLUSTER
                }

                keyOrPattern("*")

                pollDelay(Duration.ofSeconds(1))

                monitoring {
                    events = true
                    meters = true
                }
            }
            .logErrors()
            .onEach { it.map { message -> receivedMessages.add(message.value) } }
            .onEach { println(it) }
    }

    @Scenario
    fun pollDataScanSentinel() {
        scenario("lettuce-poll-sscan-sentinel") {
            minionsCount = 2
            rampUp {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }
            .start()
            .redisLettuce()
            .pollSscan {
                name = "poll.sscan.cluster"
                connection {
                    nodes = dbNodes
                    redisConnectionType = RedisConnectionType.SENTINEL
                    masterId = "mymaster"
                }

                keyOrPattern("A")

                pollDelay(Duration.ofSeconds(1))

                monitoring {
                    events = true
                    meters = true
                }
            }
            .logErrors()
            .onEach { it.map { message -> receivedMessages.add(message.value) } }
            .onEach { println(it) }
    }

    data class UserEvent(val username: String, val timestamp: LocalDateTime)

}
