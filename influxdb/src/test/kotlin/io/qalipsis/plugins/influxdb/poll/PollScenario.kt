package io.qalipsis.plugins.influxdb.poll

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.rampup.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.filterNotNull
import io.qalipsis.api.steps.innerJoin
import io.qalipsis.api.steps.logErrors
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.onEach
import io.qalipsis.plugins.influxdb.influxdb
import io.qalipsis.plugins.influxdb.poll.AbstractInfluxDbIntegrationTest.Companion.BUCKET
import io.qalipsis.plugins.influxdb.poll.AbstractInfluxDbIntegrationTest.Companion.ORGANIZATION
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

    @Scenario
    fun pollData() {
        scenario("influxdb-poll") {
            minionsCount = minions
            rampUp {
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
            .logErrors()
            .innerJoin(
                using = { it.value["_value"] },
                on = {
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
                },
                having = { it.value["_value"].also { log.trace { "Right: $it" } } }
            )
            .filterNotNull()
            .map { (inAction, outAction) ->

                val e = mutableMapOf<String, Long>()
                inAction.stream()
                    .forEach {
                        e[it.values["_value"] as String] = (it.values["_time"] as Instant).toEpochMilli()
                    }
                outAction.stream().forEach { it ->
                    receivedMessages.add(
                        "The user ${it.values["_value"] as String} stayed ${
                            Duration.ofMillis(
                                (it.values["_time"] as Instant).toEpochMilli() - e.get(
                                    it.values["_value"] as String
                                )!!
                            ).toMinutes()
                        } minute(s) in the building"
                    )
                }
            }
            .onEach { println(receivedMessages) }
    }
}

private operator fun Any?.get(s: String) {
}

