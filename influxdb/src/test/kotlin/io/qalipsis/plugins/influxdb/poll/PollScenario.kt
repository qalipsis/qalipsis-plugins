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

package io.qalipsis.plugins.influxdb.poll

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.filterNotNull
import io.qalipsis.api.steps.flatten
import io.qalipsis.api.steps.innerJoin
import io.qalipsis.api.steps.logErrors
import io.qalipsis.api.steps.map
import io.qalipsis.plugins.influxdb.AbstractInfluxDbIntegrationTest.Companion.BUCKET
import io.qalipsis.plugins.influxdb.AbstractInfluxDbIntegrationTest.Companion.ORGANIZATION
import io.qalipsis.plugins.influxdb.influxdb
import java.time.Duration
import java.time.Instant

/**
 *
 * Scenario to demo how the poll step can work. The scenario reads the entries in a building on one side and the exits
 * on the other side.
 *
 * Records related to the same person are joined and the duration is then printed out in the console.
 *
 * @author Eric Jessé
 */
object PollScenario {

    private const val minions = 5

    val receivedMessages = concurrentSet<String>()

    var influxDbUrl = ""

    @JvmStatic
    private val log = logger()

    @Scenario("influxdb-poll")
    fun pollData() {
        scenario {
            minionsCount = minions
            profile {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }
            .start()
            .influxdb().poll {
                name = "poll.in"
                connect {
                    server(influxDbUrl, BUCKET, ORGANIZATION)
                    basic("user", "passpasspass")
                }
                query("from(bucket: \"$BUCKET\") |> range(start: 0) |> filter(fn: (r) => (r[\"action\"] == \"IN\"))")
                pollDelay(Duration.ofSeconds(1))
            }
            .map { it.results }
            .flatten()
            .logErrors()
            .innerJoin()
            .using {
                // The user's name is in the field _value.
                it.value.getValueByKey("_value")
            }.on {
                it.influxdb().poll {
                    name = "poll.out"
                    connect {
                        server(influxDbUrl, BUCKET, ORGANIZATION)
                        basic("user", "passpasspass")
                    }
                    query("from(bucket: \"$BUCKET\") |> range(start: 0) |> filter(fn: (r) => (r[\"action\"] == \"OUT\"))")
                    pollDelay(Duration.ofSeconds(1))
                }
                    .logErrors()
                    .map {
                        log.trace { "Right record: $it" }
                        it.results
                    }
                    .flatten()
            }.having {
                it.value.getValueByKey("_value")
            }
            .filterNotNull()
            .map { (inAction, outAction) ->
                val user = inAction.values["_value"]
                val entry = inAction.values["_time"] as Instant
                val exit = outAction.values["_time"] as Instant
                val stayDuration = Duration.between(entry, exit).toMinutes()

                receivedMessages.add("The user $user stayed $stayDuration minute(s) in the building")
            }
    }
}
