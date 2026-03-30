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

package io.qalipsis.plugins.redis.lettuce.poll.scenario

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.lang.concurrentSet
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

    @Scenario("poll-sscan")
    fun pollData() {
        scenario {
            minionsCount = pollSScanMinions
            profile {
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
            .innerJoin()
            .using { it.value.username }
            .on {
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
            }
            .having { it.value.username }
            .logErrors()
            .onEach { receivedMessages.add(it!!.second.username) }
            .onEach { println(it) }
    }

    @Scenario("poll-scan")
    fun pollDataScan() {
        scenario {
            minionsCount = pollScanMinions
            profile {
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

    @Scenario("poll-hscan")
    fun pollDataHScan() {
        scenario {
            minionsCount = pollHScanMinions
            profile {
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


    @Scenario("poll-zscan")
    fun pollDataZScan() {
        scenario {
            minionsCount = pollZScanMinions
            profile {
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

    @Scenario("poll-scan-with-acl")
    fun pollDataScanAcl() {
        scenario {
            minionsCount = pollHScanMinions
            profile {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }
            .start()
            .redisLettuce()
            .pollScan {
                name = "poll.scanacl"
                connection {
                    nodes = dbNodes
                    database = dbDatabase
                    authUser = "alice"
                    authPassword = "dszcZT"
                }

                keyOrPattern("allowed-keys-*")

                pollDelay(Duration.ofSeconds(1))

                monitoring {
                    events = false
                    meters = false
                }
            }.flatten()
            .logErrors()
            .map { it.value }
            .onEach { receivedMessages.add(it) }
            .onEach { println(it) }
    }

    @Scenario("poll-scan-cluster")
    fun pollDataScanCluster() {
        scenario {
            minionsCount = pollScanClusterMinions
            profile {
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

    @Scenario("poll-scan-cluster-batch")
    fun pollDataScanClusterWithoutFlatten() {
        scenario {
            minionsCount = 2
            profile {
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

    @Scenario("poll-sscan-sentinel")
    fun pollDataScanSentinel() {
        scenario {
            minionsCount = 2
            profile {
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
