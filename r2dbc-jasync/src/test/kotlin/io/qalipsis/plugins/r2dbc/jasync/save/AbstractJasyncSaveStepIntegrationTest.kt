package io.qalipsis.plugins.r2dbc.jasync.save

import assertk.assertThat
import com.github.jasync.sql.db.SuspendingConnection
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
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
import java.util.concurrent.TimeUnit

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

    private val eventsLogger = relaxedMockk<EventsLogger>()

    private val recordsCounter = relaxedMockk<Counter>()

    private val failureCounter = relaxedMockk<Counter>()

    private val successCounter = relaxedMockk<Counter>()

    private val timeToResponse = relaxedMockk<Timer>()

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
        val recordsList = listOf(JasyncSaveRecord(listOf("2020-10-20T12:34:21", "IN", "alice", true)))
        val columns = listOf("timestamp", "action", "username", "enabled")
        val tableName = "buildingentries"
        val metersTags = relaxedMockk<Tags>()
        val meterRegistry = relaxedMockk<MeterRegistry> {
            every { counter("jasync-save-records", refEq(metersTags)) } returns recordsCounter
            every { counter("jasync-save-records-success", refEq(metersTags)) } returns successCounter
            every { timer("jasync-save-records-time-to-response", refEq(metersTags)) } returns timeToResponse
        }
        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns metersTags
        }

        val step = JasyncSaveStep<String>(
            id = id,
            retryPolicy = retryPolicyNull,
            connectionPoolBuilder = { connection },
            recordsFactory = { _, _ -> recordsList },
            columnsFactory = { _, _ -> columns },
            tableNameFactory = { _, _ -> tableName },
            dialect = dialect,
            meterRegistry = meterRegistry,
            eventsLogger = eventsLogger
        )

        val input = "input data"
        val context =
            StepTestHelper.createStepContext<String, JasyncSaveResult<String>>(input)

        step.start(startStopContext)
        step.execute(context)

        verify {
            timeToResponse.record(more(0L), TimeUnit.NANOSECONDS)
            successCounter.increment(1.0)
            recordsCounter.increment()
        }
        confirmVerified(timeToResponse, successCounter, recordsCounter)

        val result = connection.sendQuery("SELECT * FROM $tableName")
        assertTrue(result.rows[0].contains("IN"))
        assertTrue(result.rows[0].contains("alice"))

        val output = (context.output as Channel).receive()
        assertThat(output.jasyncSaveStepMeters.successSavedDocuments == 1)
        assertThat(output.input == "input data")

    }

    @Test
    @Timeout(20)
    internal fun `test save two rows success`() = runBlocking {
        val id = "step-id"
        val retryPolicyNull = null
        val recordsList = listOf(
            JasyncSaveRecord(listOf("2020-10-20T12:34:21", "IN", "alice", true)),
            JasyncSaveRecord(listOf("2020-10-20T12:44:10", "IN", "john", false))
        )
        val columns = listOf("timestamp", "action", "username", "enabled")
        val tableName = "buildingentries"

        val metersTags = relaxedMockk<Tags>()
        val meterRegistry = relaxedMockk<MeterRegistry> {
            every { counter("jasync-save-records", refEq(metersTags)) } returns recordsCounter
            every { counter("jasync-save-records-success", refEq(metersTags)) } returns successCounter
            every { timer("jasync-save-records-time-to-response", refEq(metersTags)) } returns timeToResponse
        }
        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns metersTags
        }

        val step = JasyncSaveStep<String>(
            id = id,
            retryPolicy = retryPolicyNull,
            connectionPoolBuilder = { connection },
            recordsFactory = { _, _ -> recordsList },
            columnsFactory = { _, _ -> columns },
            tableNameFactory = { _, _ -> tableName },
            dialect = dialect,
            meterRegistry = meterRegistry,
            eventsLogger = eventsLogger
        )
        val input = "input data"
        val context =
            StepTestHelper.createStepContext<String, JasyncSaveResult<String>>(input)

        step.start(startStopContext)
        step.execute(context)

        verify {
            timeToResponse.record(more(0L), TimeUnit.NANOSECONDS)
            successCounter.increment(2.0)
            recordsCounter.increment()
        }
        confirmVerified(timeToResponse, successCounter, recordsCounter)

        val result = connection.sendQuery("SELECT * FROM $tableName")
        val toCollection = result.rows.flatten()

        assertTrue(toCollection.contains("IN"))
        assertTrue(toCollection.contains("alice"))
        assertTrue(toCollection.contains("john"))

        val output = (context.output as Channel).receive()
        assertThat(output.jasyncSaveStepMeters.successSavedDocuments == 2)
        assertThat(output.input == "input data")
    }

    @Test
    @Timeout(20)
    internal fun `should fail on save`() = runBlocking {
        val id = "step-id"
        val retryPolicyNull = null
        val recordsList = listOf(JasyncSaveRecord(listOf("2020-10-20T12:34:21", "IN", "alice", "fail")))
        val columns = listOf("timestamp", "action", "username", "enabled")
        val tableName = "buildingentries"

        val metersTags = relaxedMockk<Tags>()
        val meterRegistry = relaxedMockk<MeterRegistry> {
            every { counter("jasync-save-records", refEq(metersTags)) } returns recordsCounter
            every { counter("jasync-save-records-success", refEq(metersTags)) } returns successCounter
            every { counter("jasync-save-records-failure", refEq(metersTags)) } returns failureCounter
        }

        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns metersTags
        }

        val step = JasyncSaveStep<String>(
            id = id,
            retryPolicy = retryPolicyNull,
            connectionPoolBuilder = { connection },
            recordsFactory = { _, _ -> recordsList },
            columnsFactory = { _, _ -> columns },
            tableNameFactory = { _, _ -> tableName },
            dialect = dialect,
            meterRegistry = meterRegistry,
            eventsLogger = eventsLogger
        )
        val input = "input data"
        val context =
            StepTestHelper.createStepContext<String, JasyncSaveResult<String>>(input)
        step.start(startStopContext)
        step.execute(context)

        verify {
            recordsCounter.increment()
            failureCounter.increment(1.0)
        }
        confirmVerified(recordsCounter, successCounter, failureCounter)

        val output = (context.output as Channel).receive()
        assertThat(output.input == "input data")
    }
}
