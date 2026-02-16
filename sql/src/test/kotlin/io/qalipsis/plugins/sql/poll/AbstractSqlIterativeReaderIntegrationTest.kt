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

package io.qalipsis.plugins.sql.poll

import assertk.all
import assertk.assertThat
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.aerisconsulting.catadioptre.setProperty
import io.mockk.spyk
import io.qalipsis.plugins.sql.SqlResultSet
import io.qalipsis.plugins.sql.dialect.Dialect
import io.qalipsis.plugins.sql.poll.catadioptre.init
import io.qalipsis.test.io.readResource
import io.qalipsis.test.io.readResourceLines
import io.r2dbc.pool.ConnectionPool
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.LocalDateTime

/**
 *
 * @author Eric Jessé
 */
@Testcontainers
internal abstract class AbstractSqlIterativeReaderIntegrationTest(
    scriptFolderBaseName: String,
    private val dialect: Dialect,
    private val connectionPoolFactory: () -> ConnectionPool
) : AbstractSqlIntegrationTest(connectionPoolFactory) {

    private val creationScript = readResource("schemas/$scriptFolderBaseName/create-table-events.sql").trim()

    private val dropScript = readResource("schemas/$scriptFolderBaseName/drop-table-events.sql").trim()

    protected val populateStatements = readResourceLines("schemas/$scriptFolderBaseName/populate-table-events.sql")

    protected val records = readResourceLines("events-data.csv").map { it.split(",") }

    @BeforeEach
    override fun setUp() {
        super.setUp()
        runBlocking {
            sendQuery(creationScript)
        }
    }

    @AfterEach
    internal fun tearDown(): Unit = testDispatcherProvider.run { sendQuery(dropScript) }

    /**
     * This tests imports all the data in the table, but filter the values with a WHERE clause in the query
     * to remove the ones for Truck #1.
     */
    @Test
    @Timeout(10)
    internal fun `should read all the content of the table with where clause`() = testDispatcherProvider.run {
        // given
        val firstBatch = populateStatements.subList(0, 11)
        val secondBatch = populateStatements.subList(11, 26)
        val thirdBatch = populateStatements.subList(26, 39)

        val reader = SqlIterativeReader(
            ioCoroutineScope = this,
            connectionPoolFactory = connectionPoolFactory,
            dialect = dialect,
            sqlPollStatement = SqlPollStatementImpl(
                dialect,
                """SELECT ${dialect.quote("timestamp")}, device, eventname FROM events WHERE device <> ? ORDER BY ${
                    dialect.quote(
                        "timestamp"
                    )
                } """,
                listOf("Truck #1")
            ),
            pollDelay = Duration.ofMillis(POLL_TIMEOUT),
            resultsChannelFactory = { Channel(5) }
        )
        reader.init()
        reader.setProperty("connectionPool", connectionPoolFactory())
        `populate, read and assert`(reader, firstBatch, secondBatch, thirdBatch)
    }

    /**
     * This tests only imports the data in the table, that are not related to Truck #1.
     * Then, the query has no where clause.
     */
    @Test
    @Timeout(10)
    internal fun `should read all the content of the table without where clause`() = testDispatcherProvider.run {

        // given
        val eligiblePopulateStatements = populateStatements.filterNot { it.contains("Truck #1") }
        val firstBatch = eligiblePopulateStatements.subList(0, 8)
        val secondBatch = eligiblePopulateStatements.subList(8, 18)
        val thirdBatch = eligiblePopulateStatements.subList(18, 26)

        val reader = spyk(SqlIterativeReader(
            ioCoroutineScope = this,
            connectionPoolFactory = connectionPoolFactory,
            dialect = dialect,
            sqlPollStatement = SqlPollStatementImpl(
                dialect,
                """SELECT ${dialect.quote("timestamp")}, device, eventname FROM events ORDER BY ${dialect.quote("timestamp")} """,
                emptyList()
            ),
            pollDelay = Duration.ofMillis(POLL_TIMEOUT),
            resultsChannelFactory = { Channel(5) }
        ), recordPrivateCalls = true)
        reader.init()
        reader.setProperty("connectionPool", connectionPoolFactory())
        `populate, read and assert`(reader, firstBatch, secondBatch, thirdBatch)
    }

    /**
     * Populates the table batch by batch, and verifies the fetched data at each stage.
     *
     * Since the delivery strategy is "at least once", the bound records of the batches are repeated in the next poll.
     */
    private suspend fun `populate, read and assert`(
        reader: SqlIterativeReader,
        firstBatch: List<String>,
        secondBatch: List<String>,
        thirdBatch: List<String>
    ) {
        val eligibleRecords = records.filter { it[1] != "Truck #1" }

        // when
        // Executes a first poll to verify that no empty set is provided.
        reader.coInvokeInvisible<Unit>("poll")

        execute(firstBatch)
        assertThat(count("events")).isEqualTo(firstBatch.size)
        reader.coInvokeInvisible<Unit>("poll")

        execute(secondBatch)
        assertThat(count("events")).isEqualTo(firstBatch.size + secondBatch.size)
        reader.coInvokeInvisible<Unit>("poll")

        execute(thirdBatch)
        assertThat(count("events")).isEqualTo(firstBatch.size + secondBatch.size + thirdBatch.size)
        reader.coInvokeInvisible<Unit>("poll")

        // then
        val firstFetchedBatch = reader.next()
        val secondFetchedBatch = reader.next()
        val thirdFetchedBatch = reader.next()

        assertThat(firstFetchedBatch).all {
            prop(SqlResultSet::size).isEqualTo(8)
            (0..7).forEach { index ->
                index(index).all {
                    transform { LocalDateTime.parse(it["timestamp"].toString()) }.isEqualTo(
                        LocalDateTime.parse(
                            eligibleRecords[index][0]
                        )
                    )
                    transform { it["device"] as String }.isEqualTo(eligibleRecords[index][1])
                    transform { it["eventname"] as String }.isEqualTo(eligibleRecords[index][2])
                }
            }
        }

        assertThat(secondFetchedBatch).all {
            prop(SqlResultSet::size).isEqualTo(12)
            (0..11).forEach { index ->
                val eligibleRecordsIndex = index + 6
                index(index).all {
                    transform { LocalDateTime.parse(it["timestamp"].toString()) }.isEqualTo(
                        LocalDateTime.parse(
                            eligibleRecords[eligibleRecordsIndex][0]
                        )
                    )
                    transform { it["device"] as String }.isEqualTo(eligibleRecords[eligibleRecordsIndex][1])
                    transform { it["eventname"] as String }.isEqualTo(eligibleRecords[eligibleRecordsIndex][2])
                }
            }
        }

        assertThat(thirdFetchedBatch).all {
            prop(SqlResultSet::size).isEqualTo(10)
            (0..9).forEach { index ->
                val eligibleRecordsIndex = index + 16
                index(index).all {
                    transform { LocalDateTime.parse(it["timestamp"].toString()) }.isEqualTo(
                        LocalDateTime.parse(
                            eligibleRecords[eligibleRecordsIndex][0]
                        )
                    )
                    transform { it["device"] as String }.isEqualTo(eligibleRecords[eligibleRecordsIndex][1])
                    transform { it["eventname"] as String }.isEqualTo(eligibleRecords[eligibleRecordsIndex][2])
                }
            }
        }
    }

    companion object {

        private const val POLL_TIMEOUT = 1000L

    }
}
