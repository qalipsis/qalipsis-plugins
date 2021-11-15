package io.qalipsis.plugins.r2dbc.jasync.save

import com.github.jasync.sql.db.SuspendingConnection
import com.github.jasync.sql.db.mysql.MySQLQueryResult
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepId
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.plugins.r2dbc.jasync.dialect.Dialect
import java.time.Duration

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
    private val metrics: JasyncQueryMetrics
) : AbstractStep<I, R2DBCSaveResult<I>>(id, retryPolicy) {

    private lateinit var connection: SuspendingConnection

    override suspend fun start(context: StepStartStopContext) {
        connection = connectionPoolBuilder()
        connection.connect()
    }

    override suspend fun stop(context: StepStartStopContext) {
        connection.disconnect()
    }

    override suspend fun execute(context: StepContext<I, R2DBCSaveResult<I>>) {
        val input = context.receive()
        val tableName = tableNameFactory(context, input)
        val columns = columnsFactory(context, input)
        val parameters = recordsFactory(context, input)
        var timeToSuccess: Duration? = null
        var timeToFailure: Duration? = null
        val ids = mutableListOf<Long>()

        val requestStart = System.nanoTime()
        parameters.forEach {
            try {
                val result =
                    connection.sendPreparedStatement(buildQuery(dialect, tableName, columns), it.parameters, true)
                val timer = System.nanoTime() - requestStart
                // There is only one implementation of QueryResult, called MySQLQueryResult, whether
                // the DB is MySQL, MariaDB or PgSQL.
                val queryResult = result as MySQLQueryResult
                ids.add(queryResult.lastInsertId)
                timeToSuccess = Duration.ofMillis(timer)
                metrics.countSuccess()
                metrics.recordTimeToSuccess(timer)
                metrics.countRecords(result.rowsAffected.toInt())
            } catch (e: Exception) {
                val timer = System.nanoTime() - requestStart
                timeToFailure = Duration.ofMillis(timer)
                metrics.countFailure()
                metrics.recordTimeToFailure(timer)
                context.addError(StepError(e))
            }
        }

        context.send(
            R2DBCSaveResult(
                input,
                ids,
                timeToSuccess,
                timeToFailure,
                successSavedDocuments = metrics.successCounter?.count()?.toInt(),
                failedSavedDocuments = metrics.failureCounter?.count()?.toInt()
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
