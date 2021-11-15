package io.qalipsis.plugins.r2dbc.jasync.save

import assertk.assertThat
import com.github.jasync.sql.db.SuspendingConnection
import io.mockk.confirmVerified
import io.mockk.verify
import io.qalipsis.plugins.r2dbc.jasync.dialect.Dialect
import io.qalipsis.plugins.r2dbc.jasync.poll.AbstractJasyncIntegrationTest
import io.qalipsis.test.io.readResource
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Integration test for the usage of the save step
 *
 * @author Carlos Vieira
 */
@WithMockk
@Testcontainers
internal abstract class AbstractJasyncSaveStepIntegrationTest(
    scriptFolderBaseName: String,
    private val dialect: Dialect,
    connectionPoolFactory: () -> SuspendingConnection
) : AbstractJasyncIntegrationTest(connectionPoolFactory) {

    private val jasyncQueryMetrics = relaxedMockk<JasyncQueryMetrics>()

    private val creationScript = readResource("schemas/$scriptFolderBaseName/create-table-buildingentries.sql").trim()
    private val dropScript = readResource("schemas/$scriptFolderBaseName/drop-table-buildingentries.sql").trim()

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
    internal fun `should run the save`() = runBlocking {
        val id = "step-id"
        val retryPolicyNull = null
        val recordsList = listOf(JasyncSaverRecord(listOf("2020-10-20T12:34:21", "IN", "alice", true)))
        val columns = listOf("timestamp", "action", "username", "enabled")
        val tableName = "buildingentries"

        val step = JasyncSaveStep<String>(
            id = id,
            retryPolicy = retryPolicyNull,
            connectionPoolBuilder = { connection },
            recordsFactory = { _, _ -> recordsList },
            columnsFactory = { _, _ -> columns },
            tableNameFactory = { _, _ -> tableName },
            metrics = jasyncQueryMetrics,
            dialect = dialect,
        )

        val input = "input data"
        val context =
            StepTestHelper.createStepContext<String, R2DBCSaveResult<String>>(input)

        step.start(relaxedMockk())
        step.execute(context)

        verify {
            jasyncQueryMetrics.recordTimeToSuccess(more(0L))
            jasyncQueryMetrics.countRecords(eq(1))
            jasyncQueryMetrics.countSuccess()
        }
        confirmVerified(jasyncQueryMetrics)

        val result = connection.sendQuery("SELECT * FROM $tableName")
        assertTrue(result.rows[0].contains("IN"))
        assertTrue(result.rows[0].contains("alice"))

        val output = (context.output as Channel).receive()
        assertThat(output.successSavedDocuments == 1)
        assertThat(output.input == "input data")

    }

    @Test
    @Timeout(20)
    internal fun `test save two rows success`() = runBlocking {
        val id = "step-id"
        val retryPolicyNull = null
        val recordsList = listOf(
            JasyncSaverRecord(listOf("2020-10-20T12:34:21", "IN", "alice", true)),
            JasyncSaverRecord(listOf("2020-10-20T12:44:10", "IN", "john", false))
        )
        val columns = listOf("timestamp", "action", "username", "enabled")
        val tableName = "buildingentries"
        val step = JasyncSaveStep<String>(
            id = id,
            retryPolicy = retryPolicyNull,
            connectionPoolBuilder = { connection },
            recordsFactory = { _, _ -> recordsList },
            columnsFactory = { _, _ -> columns },
            tableNameFactory = { _, _ -> tableName },
            dialect = dialect,
            metrics = jasyncQueryMetrics,
        )
        val input = "input data"
        val context =
            StepTestHelper.createStepContext<String, R2DBCSaveResult<String>>(input)

        step.start(relaxedMockk())
        step.execute(context)

        verify {
            jasyncQueryMetrics.recordTimeToSuccess(more(0L))
            jasyncQueryMetrics.countRecords(eq(2))
            jasyncQueryMetrics.countSuccess()
        }
        confirmVerified(jasyncQueryMetrics)

        val result = connection.sendQuery("SELECT * FROM $tableName")
        val toCollection = result.rows.flatten()

        assertTrue(toCollection.contains("IN"))
        assertTrue(toCollection.contains("alice"))
        assertTrue(toCollection.contains("john"))

        val output = (context.output as Channel).receive()
        assertThat(output.successSavedDocuments == 2)
        assertThat(output.input == "input data")
    }

    @Test
    @Timeout(20)
    internal fun `should fail on save`() = runBlocking {
        val id = "step-id"
        val retryPolicyNull = null
        val recordsList = listOf(JasyncSaverRecord(listOf("2020-10-20T12:34:21", "IN", "alice", "fail")))
        val columns = listOf("timestamp", "action", "username", "enabled")
        val tableName = "buildingentries"

        val step = JasyncSaveStep<String>(
            id = id,
            retryPolicy = retryPolicyNull,
            connectionPoolBuilder = { connection },
            recordsFactory = { _, _ -> recordsList },
            columnsFactory = { _, _ -> columns },
            tableNameFactory = { _, _ -> tableName },
            dialect = dialect,
            metrics = jasyncQueryMetrics,
        )
        val input = "input data"
        val context =
            StepTestHelper.createStepContext<String, R2DBCSaveResult<String>>(input)
        step.start(relaxedMockk())
        step.execute(context)

        verify {
            jasyncQueryMetrics.recordTimeToFailure(more(0L))
            jasyncQueryMetrics.countRecords(eq(1))
            jasyncQueryMetrics.countFailure()
        }
        confirmVerified(jasyncQueryMetrics)

        val output = (context.output as Channel).receive()
        assertThat(output.failedSavedDocuments == 1)
        assertThat(output.input == "input data")
    }
}
