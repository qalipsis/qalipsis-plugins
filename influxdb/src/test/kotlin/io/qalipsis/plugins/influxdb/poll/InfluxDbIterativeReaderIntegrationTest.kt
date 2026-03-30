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

package io.qalipsis.plugins.influxdb.poll

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.key
import assertk.assertions.prop
import com.influxdb.client.domain.WritePrecision
import com.influxdb.query.FluxRecord
import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.qalipsis.plugins.influxdb.AbstractInfluxDbIntegrationTest
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration
import java.time.Instant
import java.time.Period

internal class InfluxDbIterativeReaderIntegrationTest : AbstractInfluxDbIntegrationTest() {

    private lateinit var reader: InfluxDbIterativeReader

    @Test
    @Timeout(20)
    fun `should save data and poll them`() = testDispatcherProvider.run {
        // given
        val queryString = """from(bucket: "$BUCKET") |> range(start: 0)
                |> filter(fn: (r) => (r["_measurement"] == "likes"))"""
        val pollStatement = InfluxDbPollStatement(queryString)
        reader = InfluxDbIterativeReader(
            clientFactory = { client },
            pollStatement = pollStatement,
            pollDelay = Duration.ofMillis(300),
            resultsChannelFactory = { Channel(Channel.UNLIMITED) },
            coroutineScope = this,
            eventsLogger = null,
            meterRegistry = null
        )
        reader.init()
        val writeApi = client.getWriteKotlinApi()

        // when
        val data1 = "likes,host=host1 idle=\"55\" " + Instant.now().minus(Period.ofDays(2)).toEpochMilli() * 1000000
        writeApi.writeRecord(data1, WritePrecision.NS, BUCKET, ORGANIZATION)

        reader.coInvokeInvisible<Unit>("poll", client) // Should only fetch the first record.

        val data2 = "likes,host=host2 idle=\"80\" " + Instant.now().minus(Period.ofDays(1)).toEpochMilli() * 1000000
        writeApi.writeRecord(data2, WritePrecision.NS, BUCKET, ORGANIZATION)

        reader.coInvokeInvisible<Unit>("poll", client) // Should fetch the first and second record.
        reader.coInvokeInvisible<Unit>("poll", client) // Should only fetch the second record.

        // then
        assertThat(reader.next()).all {
            prop(InfluxDbQueryResult::results).all {
                hasSize(1)
                index(0).all {
                    prop(FluxRecord::getValue).isEqualTo("55")
                    prop(FluxRecord::getValues).key("host").isEqualTo("host1")
                }
            }
            prop(InfluxDbQueryResult::meters).all {
                prop(InfluxDbQueryMeters::fetchedRecords).isEqualTo(1)
                prop(InfluxDbQueryMeters::timeToResult).isGreaterThan(Duration.ZERO)
            }
        }
        assertThat(reader.next()).all {
            prop(InfluxDbQueryResult::results).all {
                hasSize(2)
                index(0).all {
                    prop(FluxRecord::getValue).isEqualTo("55")
                    prop(FluxRecord::getValues).key("host").isEqualTo("host1")
                }
                index(1).all {
                    prop(FluxRecord::getValue).isEqualTo("80")
                    prop(FluxRecord::getValues).key("host").isEqualTo("host2")
                }
            }
            prop(InfluxDbQueryResult::meters).all {
                prop(InfluxDbQueryMeters::fetchedRecords).isEqualTo(2)
                prop(InfluxDbQueryMeters::timeToResult).isGreaterThan(Duration.ZERO)
            }
        }
        assertThat(reader.next()).all {
            prop(InfluxDbQueryResult::results).all {
                hasSize(1)
                index(0).all {
                    prop(FluxRecord::getValue).isEqualTo("80")
                    prop(FluxRecord::getValues).key("host").isEqualTo("host2")
                }
            }
            prop(InfluxDbQueryResult::meters).all {
                prop(InfluxDbQueryMeters::fetchedRecords).isEqualTo(1)
                prop(InfluxDbQueryMeters::timeToResult).isGreaterThan(Duration.ZERO)
            }
        }

        reader.stop(relaxedMockk())
    }

    @Test
    @Timeout(20)
    fun `should save data and poll shen sorting descending with the value`() = testDispatcherProvider.run {
        // given
        val queryString =
            """from(bucket: "$BUCKET") |> range(start: 0) 
                |> filter(fn: (r) => (r["_measurement"] == "moves"))
                |> sort(desc: true)"""

        val pollStatement = InfluxDbPollStatement(queryString)
        reader = InfluxDbIterativeReader(
            clientFactory = { client },
            pollStatement = pollStatement,
            pollDelay = Duration.ofMillis(300),
            resultsChannelFactory = { Channel(Channel.UNLIMITED) },
            coroutineScope = this,
            eventsLogger = null,
            meterRegistry = null
        )
        reader.init()
        val uniqueTimestamp = Instant.now().minus(Period.ofDays(1)).toEpochMilli() * 1000000

        val writeApi = client.getWriteKotlinApi()

        // when
        val data0 = """moves,name="Poli,age=19 class=7 $uniqueTimestamp"""
        writeApi.writeRecord(data0, WritePrecision.NS, BUCKET, ORGANIZATION)

        reader.coInvokeInvisible<Unit>("poll", client)

        val data1 = """moves,name="Vova",age=17 class=9 $uniqueTimestamp"""
        val data2 = """moves,name="Dasha",age=43 class=5  $uniqueTimestamp"""
        val data3 = """moves,name="Yana",age=20 class=3 $uniqueTimestamp"""
        val data = listOf(data1, data2, data3)
        writeApi.writeRecords(data, WritePrecision.NS, BUCKET, ORGANIZATION)

        reader.coInvokeInvisible<Unit>("poll", client)
        reader.coInvokeInvisible<Unit>("poll", client)

        val data4 = """moves,name="Serge",age=80 class=6 $uniqueTimestamp"""
        val data5 = """moves,name="Sasha",age=55 class=2 $uniqueTimestamp"""
        val dataNew = listOf(data4, data5)
        writeApi.writeRecords(dataNew, WritePrecision.NS, BUCKET, ORGANIZATION)

        reader.coInvokeInvisible<Unit>("poll", client)
        reader.coInvokeInvisible<Unit>("poll", client)

        // then
        assertThat(reader.next()).prop(InfluxDbQueryResult::results).all {
            hasSize(1)
            index(0).prop(FluxRecord::getValue).isEqualTo(7.0)
        }
        assertThat(reader.next()).prop(InfluxDbQueryResult::results).all {
            hasSize(3)
            index(0).prop(FluxRecord::getValue).isEqualTo(7.0)
            index(1).prop(FluxRecord::getValue)
                .isEqualTo(3.0) // FIXME Note for a weird reason, the values are actually not sorted.
            index(2).prop(FluxRecord::getValue).isEqualTo(5.0)
        }
        assertThat(reader.next()).prop(InfluxDbQueryResult::results).all {
            hasSize(1)
            index(0).prop(FluxRecord::getValue).isEqualTo(3.0)
        }
        assertThat(reader.next()).prop(InfluxDbQueryResult::results).all {
            hasSize(2)
            index(0).prop(FluxRecord::getValue).isEqualTo(3.0)
            index(1).prop(FluxRecord::getValue).isEqualTo(2.0)
        }
        assertThat(reader.next()).prop(InfluxDbQueryResult::results).all {
            hasSize(1)
            index(0).prop(FluxRecord::getValue).isEqualTo(2.0)
        }

        reader.stop(relaxedMockk())
    }
}