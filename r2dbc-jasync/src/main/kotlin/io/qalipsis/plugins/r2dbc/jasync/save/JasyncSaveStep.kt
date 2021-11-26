package io.qalipsis.plugins.r2dbc.jasync.save

import com.github.jasync.sql.db.SuspendingConnection
import com.github.jasync.sql.db.mysql.MySQLQueryResult
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepId
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.plugins.r2dbc.jasync.dialect.Dialect
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Implementation of a [io.qalipsis.api.steps.Step] able to perform a save on the database.
 *
 * @property connectionPoolBuilder closure to generate connection to database.
 * @property tableName name of the database.
 * @property columns name of the columns on the database.
 * @property recordsFactory list of rows to add to database.
 * @property metrics metrics to track.
 *
 * @author Carlos Vieira
 */
internal class JasyncSaveStep<I>(
    id: StepId,
    retryPolicy: RetryPolicy?,
    private val connectionPoolBuilder: () -> SuspendingConnection,
    private val dialect: Dialect,
    private val tableNameFactory: suspend (ctx: StepContext<*, *>, input: I) -> String,
    private val columnsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<String>,
    private val recordsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<JasyncSaverRecord>,
    private val meterRegistry: MeterRegistry?,
    private val eventsLogger: EventsLogger?
) : AbstractStep<I, JasyncSaveResult<I>>(id, retryPolicy) {

    private lateinit var connection: SuspendingConnection

    private val eventPrefix: String = "jasync.save"

    private val meterPrefix: String = "jasync-save"

    private var recordsCounter: Counter? = null

    private var successCounter: Counter? = null

    private var timeToResponse: Timer? = null

    private var failureCounter: Counter? = null

    private lateinit var eventTags: Map<String, String>

    override suspend fun start(context: StepStartStopContext) {
        meterRegistry?.apply {
            val tags = context.toMetersTags()
            recordsCounter = counter("$meterPrefix-records", tags)
            failureCounter = counter("$meterPrefix-records-failure", tags)
            successCounter = counter("$meterPrefix-records-success", tags)
            timeToResponse = timer("$meterPrefix-records-time-to-response", tags)

        }
        eventTags = context.toEventTags();
        connection = connectionPoolBuilder()
        connection.connect()
    }

    override suspend fun stop(context: StepStartStopContext) {
        meterRegistry?.apply {
            remove(recordsCounter!!)
            remove(failureCounter!!)
            remove(successCounter!!)
            remove(timeToResponse!!)
            recordsCounter = null
            failureCounter = null
            successCounter = null
            timeToResponse = null
        }
        connection.disconnect()
    }

    override suspend fun execute(context: StepContext<I, JasyncSaveResult<I>>) {
        val input = context.receive()
        val tableName = tableNameFactory(context, input)
        val columns = columnsFactory(context, input)
        val records = recordsFactory(context, input)
        val ids = mutableListOf<Long>()

        var successSavedDocuments = 0
        var failedSavedDocuments = 0
        eventsLogger?.info("${eventPrefix}.records", records.size, tags = eventTags)
        recordsCounter?.increment(records.size.toDouble())
        val requestStart = System.nanoTime()
        records.forEach {
            try {
                val result =
                    connection.sendPreparedStatement(buildQuery(dialect, tableName, columns), it.parameters, true)
                // There is only one implementation of QueryResult, called MySQLQueryResult, whether
                // the DB is MySQL, MariaDB or PgSQL.
                val queryResult = result as MySQLQueryResult
                ids.add(queryResult.lastInsertId)
                successSavedDocuments++
            } catch (e: Exception) {
                failedSavedDocuments++
                context.addError(StepError(e))
            }
        }
        val timeToResponse = System.nanoTime() - requestStart
        eventsLogger?.info("${eventPrefix}.records.time-to-response", Duration.ofMillis(timeToResponse), tags = eventTags)
        eventsLogger?.info("${eventPrefix}.records.success", successSavedDocuments, tags = eventTags)
        if (failedSavedDocuments > 0) {
            eventsLogger?.info("${eventPrefix}.records.failure", failedSavedDocuments, tags = eventTags)
            failureCounter?.increment(failedSavedDocuments.toDouble())
        }

        val jasyncSaveStepMeters = JasyncQueryMeters(
            totalDocuments = records.size,
            timeToResponse = Duration.ofMillis(timeToResponse),
            successSavedDocuments = successSavedDocuments
        )
        successCounter?.increment(jasyncSaveStepMeters.successSavedDocuments.toDouble())
        this.timeToResponse?.record(timeToResponse,  TimeUnit.NANOSECONDS)

        context.send(
            JasyncSaveResult(
                input,
                ids,
                jasyncSaveStepMeters
            )
        )
    }

    /**
     * Creates the prepared statements to insert a record into the table.
     */
    private fun buildQuery(dialect: Dialect, tableName: String, columns: List<String>): String {
        val quotedTableName = dialect.quote(tableName)
        val quotedColumns = columns.joinToString { dialect.quote(it) }
        val arguments = columns.joinToString { "?" }
        return "INSERT INTO $quotedTableName ($quotedColumns) VALUES ($arguments)"
    }
}
