package io.qalipsis.plugins.influxdb.poll

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.client.write.Point
import io.qalipsis.plugins.influxdb.AbstractInfluxDbIntegrationTest
import io.qalipsis.runtime.test.QalipsisTestRunner
import io.qalipsis.test.io.readResourceLines
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Instant
import java.time.LocalDate


/**
 * Integration test to demo the usage of the poll operator in a scenario.
 *
 * See [PollScenario] for more details.
 */
internal class InfluxDbPollScenarioIntegrationTest : AbstractInfluxDbIntegrationTest() {

    @Test
    @Timeout(30)
    internal fun `should run the poll scenario`() = testDispatcherProvider.run {
        // given
        PollScenario.influxDbUrl = influxDBContainer.url
        populateInfluxFromCsv(client, "input/building-moves.csv")

        // when
        val exitCode = QalipsisTestRunner.withScenarios("influxdb-poll").execute()

        // then
        Assertions.assertEquals(0, exitCode)
        assertThat(PollScenario.receivedMessages).all {
            hasSize(5)
            containsExactlyInAnyOrder(
                "The user alice stayed 50 minute(s) in the building",
                "The user bob stayed 20 minute(s) in the building",
                "The user charles stayed 1 minute(s) in the building",
                "The user david stayed 114 minute(s) in the building",
                "The user erin stayed 70 minute(s) in the building"
            )
        }
    }

    private suspend fun populateInfluxFromCsv(client: InfluxDBClientKotlin, name: String) {
        val points = dbRecordsFromCsv(name)
        client.getWriteKotlinApi().writePoints(points)
    }

    private fun dbRecordsFromCsv(name: String): List<Point> {
        return this.readResourceLines(name)
            .map {
                val values = it.replace("{date}", YESTERDAY).split(",")
                val timestamp = Instant.parse(values[0]).toEpochMilli() * 1000000
                Point.measurement("moves")
                    .addTag("action", values[1])
                    .time(timestamp, WritePrecision.NS)
                    .addField("username", values[2])
            }
    }

    private companion object {

        val YESTERDAY = "${LocalDate.now().minusDays(1)}"

    }
}