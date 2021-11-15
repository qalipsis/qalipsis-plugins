package io.qalipsis.plugins.r2dbc.jasync.search

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jasync.sql.db.ResultSet
import com.github.jasync.sql.db.SuspendingConnection
import io.micrometer.core.instrument.Counter
import io.mockk.coEvery
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.steps.datasource.DatasourceRecord
import io.qalipsis.plugins.r2dbc.jasync.converters.JasyncResultSetBatchConverter
import io.qalipsis.plugins.r2dbc.jasync.converters.JasyncResultSetConverter
import io.qalipsis.plugins.r2dbc.jasync.converters.ResultValuesConverter
import io.qalipsis.plugins.r2dbc.jasync.poll.AbstractJasyncIntegrationTest
import io.qalipsis.test.io.readResource
import io.qalipsis.test.io.readResourceLines
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.joda.time.LocalDateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Integration test to demo the usage of the search step
 *
 * @author Fiodar Hmyza
 */
@Suppress("UNCHECKED_CAST")
@WithMockk
@Testcontainers
internal abstract class AbstractJasyncSearchStepIntegrationTest(
    private val scriptFolderBaseName: String,
    connectionPoolFactory: () -> SuspendingConnection
) : AbstractJasyncIntegrationTest(connectionPoolFactory) {

    @RelaxedMockK
    lateinit var counter: Counter

    @RelaxedMockK
    lateinit var resultValuesConverter: ResultValuesConverter

    val creationScript = readResource("schemas/$scriptFolderBaseName/create-table-buildingentries.sql").trim()

    val dropScript = readResource("schemas/$scriptFolderBaseName/drop-table-buildingentries.sql").trim()

    @BeforeEach
    override fun setUp() {
        super.setUp()
        runBlocking {
            connection.sendQuery(creationScript)
        }
    }

    @AfterEach
    internal fun tearDown(): Unit = runBlocking { connection.sendQuery(dropScript) }

    @Test
    @Timeout(20)
    internal fun `should run the search`() = runBlocking {
        val query =
            "select username, timestamp from buildingentries where action = ? and enabled = ? order by timestamp"
        val parameters = listOf("IN", false)
        val converter = JasyncResultSetBatchConverter<String>(resultValuesConverter, counter)
        val step = JasyncSearchStep<String>(
            id = "step-id",
            retryPolicy = null,
            connectionsPoolFactory = { connection },
            queryFactory = { _, _ -> query },
            parametersFactory = { _, _ -> parameters },
            converter = converter as JasyncResultSetConverter<ResultSet, Any?, String>
        )
        val input = "input data"
        val context =
            StepTestHelper.createStepContext<String, Pair<String, List<DatasourceRecord<Map<String, Any?>>>>>(input)
        val expected = input to listOf(
            DatasourceRecord(
                ordinal = 0, value = mapOf(
                    "username" to "bob",
                    "timestamp" to LocalDateTime(2020, 10, 20, 12, 34, 21)
                )
            ),
            DatasourceRecord(
                ordinal = 1, value = mapOf(
                    "username" to "david",
                    "timestamp" to LocalDateTime(2020, 10, 20, 12, 56, 8)
                )
            ),
            DatasourceRecord(
                ordinal = 2, value = mapOf(
                    "username" to "erin",
                    "timestamp" to LocalDateTime(2020, 10, 20, 13, 45, 8)
                )
            )
        )

        val populateStatements = readResourceLines("schemas/$scriptFolderBaseName/populate-table-buildingentries.sql")
        execute(populateStatements)

        coEvery { resultValuesConverter.process(any()) } returnsArgument 0

        step.start(relaxedMockk())
        step.execute(context)

        val output = (context.output as Channel).receive()
        assertThat(output).isEqualTo(expected)

        coVerifyOnce {
            counter.increment(3.0)
        }
    }
}
