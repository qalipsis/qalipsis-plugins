package io.qalipsis.plugins.r2dbc.jasync.poll

import assertk.all
import assertk.assertThat
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import com.github.jasync.sql.db.ResultSet
import com.github.jasync.sql.db.SuspendingConnection
import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.mockk.spyk
import io.qalipsis.plugins.r2dbc.jasync.dialect.Dialect
import io.qalipsis.plugins.r2dbc.jasync.poll.catadioptre.init
import io.qalipsis.test.io.readResource
import io.qalipsis.test.io.readResourceLines
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
internal abstract class AbstractJasyncIterativeReaderIntegrationTest(
    scriptFolderBaseName: String,
    private val dialect: Dialect,
    private val connectionPoolFactory: () -> SuspendingConnection
) : AbstractJasyncIntegrationTest(connectionPoolFactory) {

    private val creationScript = readResource("schemas/$scriptFolderBaseName/create-table-events.sql").trim()

    private val dropScript = readResource("schemas/$scriptFolderBaseName/drop-table-events.sql").trim()

    protected val populateStatements = readResourceLines("schemas/$scriptFolderBaseName/populate-table-events.sql")

    protected val records = readResourceLines("events-data.csv").map { it.split(",") }

    @BeforeEach
    override fun setUp() {
        super.setUp()
        runBlocking {
            connection.sendQuery(creationScript)
        }
    }

    @AfterEach
    internal fun tearDown(): Unit = testDispatcherProvider.run { connection.sendQuery(dropScript) }

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

        val reader = JasyncIterativeReader(
            ioCoroutineScope = this,
            connectionPoolFactory = connectionPoolFactory,
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

        val reader = spyk(JasyncIterativeReader(
            ioCoroutineScope = this,
            connectionPoolFactory = connectionPoolFactory,
            sqlPollStatement = SqlPollStatementImpl(
                dialect,
                """SELECT ${dialect.quote("timestamp")}, device, eventname FROM events ORDER BY ${dialect.quote("timestamp")} """,
                emptyList()
            ),
            pollDelay = Duration.ofMillis(POLL_TIMEOUT),
            resultsChannelFactory = { Channel(5) }
        ), recordPrivateCalls = true)
        reader.init()
        `populate, read and assert`(reader, firstBatch, secondBatch, thirdBatch)
    }

    /**
     * Populates the table batch by batch, and verifies the fetched data at each stage.
     *
     * Since the delivery strategy is "at least once", the bound records of the batches are repeated in the next poll.
     */
    private suspend fun `populate, read and assert`(
        reader: JasyncIterativeReader,
        firstBatch: List<String>,
        secondBatch: List<String>,
        thirdBatch: List<String>
    ) {
        val eligibleRecords = records.filter { it[1] != "Truck #1" }
        val connection = connectionPoolFactory()

        // when
        // Executes a first poll to verify that no empty set is provided.
        reader.coInvokeInvisible<Unit>("poll", connection)

        execute(firstBatch)
        assertThat(count("events")).isEqualTo(firstBatch.size)
        reader.coInvokeInvisible<Unit>("poll", connection)

        execute(secondBatch)
        assertThat(count("events")).isEqualTo(firstBatch.size + secondBatch.size)
        reader.coInvokeInvisible<Unit>("poll", connection)

        execute(thirdBatch)
        assertThat(count("events")).isEqualTo(firstBatch.size + secondBatch.size + thirdBatch.size)
        reader.coInvokeInvisible<Unit>("poll", connection)

        // then
        val firstFetchedBatch = reader.next()
        val secondFetchedBatch = reader.next()
        val thirdFetchedBatch = reader.next()

        assertThat(firstFetchedBatch).all {
            prop(ResultSet::size).isEqualTo(8)
            (0..7).forEach { index ->
                index(index).all {
                    transform { LocalDateTime.parse(it.getDate("timestamp").toString()) }.isEqualTo(
                        LocalDateTime.parse(
                            eligibleRecords[index][0]
                        )
                    )
                    transform { it.getString("device") }.isEqualTo(eligibleRecords[index][1])
                    transform { it.getString("eventname") }.isEqualTo(eligibleRecords[index][2])
                }
            }
        }

        assertThat(secondFetchedBatch).all {
            prop(ResultSet::size).isEqualTo(12)
            (0..11).forEach { index ->
                val eligibleRecordsIndex = index + 6
                index(index).all {
                    transform { LocalDateTime.parse(it.getDate("timestamp").toString()) }.isEqualTo(
                        LocalDateTime.parse(
                            eligibleRecords[eligibleRecordsIndex][0]
                        )
                    )
                    transform { it.getString("device") }.isEqualTo(eligibleRecords[eligibleRecordsIndex][1])
                    transform { it.getString("eventname") }.isEqualTo(eligibleRecords[eligibleRecordsIndex][2])
                }
            }
        }

        assertThat(thirdFetchedBatch).all {
            prop(ResultSet::size).isEqualTo(10)
            (0..9).forEach { index ->
                val eligibleRecordsIndex = index + 16
                index(index).all {
                    transform { LocalDateTime.parse(it.getDate("timestamp").toString()) }.isEqualTo(
                        LocalDateTime.parse(
                            eligibleRecords[eligibleRecordsIndex][0]
                        )
                    )
                    transform { it.getString("device") }.isEqualTo(eligibleRecords[eligibleRecordsIndex][1])
                    transform { it.getString("eventname") }.isEqualTo(eligibleRecords[eligibleRecordsIndex][2])
                }
            }
        }
    }

    companion object {

        private const val POLL_TIMEOUT = 1000L

    }
}
