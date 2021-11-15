package io.qalipsis.plugins.r2dbc.jasync.poll

import com.github.jasync.sql.db.ResultSet
import com.github.jasync.sql.db.SuspendingConnection
import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.datasource.DatasourceIterativeReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Duration

/**
 * Database reader based upon [JAsync Driver][https://github.com/jasync-sql/jasync-sql] for MySQL, MariaDB (using the MySQL connection) and PgSQL.
 *
 * @property connectionPoolFactory supplier for the DB connection
 * @property sqlPollStatement statement to execute
 * @property tieBreaker name of the tie-breaker column
 * @property pollDelay duration between the end of a poll and the start of the next one
 * @property resultsChannelFactory factory to create the channel containing the received results sets
 * @property running running state of the reader
 * @property pollingJob instance of the background job polling data from the database
 *
 * @author Eric Jessé
 */
internal class JasyncIterativeReader(
    private val ioCoroutineScope: CoroutineScope,
    private val connectionPoolFactory: () -> SuspendingConnection,
    private val sqlPollStatement: SqlPollStatement,
    private val tieBreaker: String,
    private val pollDelay: Duration,
    private val resultsChannelFactory: () -> Channel<ResultSet>
) : DatasourceIterativeReader<ResultSet> {

    private var running = false

    private var pollingJob: Job? = null

    private var resultsChannel: Channel<ResultSet>? = null

    override fun start(context: StepStartStopContext) {
        init()
        val connection = connectionPoolFactory()
        running = true
        pollingJob = ioCoroutineScope.launch {
            try {
                while (running) {
                    poll(connection)
                    if (running) {
                        delay(pollDelay.toMillis())
                    }
                }
            } finally {
                connection.disconnect()
                resultsChannel?.close()
                resultsChannel = null
            }
        }
    }

    @KTestable
    private fun init() {
        resultsChannel = resultsChannelFactory()
    }

    /**
     * Polls next available batch of records from the database.
     *
     * @param connection the active connection
     */
    private suspend fun poll(
        connection: SuspendingConnection
    ) {
        try {
            val result = connection.sendPreparedStatement(sqlPollStatement.query, sqlPollStatement.parameters, true)
            if (result.rowsAffected > 0) {
                log.debug { "A result set with ${result.rowsAffected} records(s) was received" }
                resultsChannel?.send(result.rows)
                val lastResult = result.rows.toList().last()
                sqlPollStatement.tieBreaker = lastResult[tieBreaker]
                log.debug { "New value for the tie-breaker is ${sqlPollStatement.tieBreaker}" }
            } else {
                log.debug { "An empty result set was received" }
                result.statusMessage?.let { log.debug { it } }
            }
        } catch (e: InterruptedException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Logs the error but allow next poll.
            log.error(e) { e.message }
        }
    }

    override fun stop(context: StepStartStopContext) {
        running = false
        runCatching {
            runBlocking {
                pollingJob?.cancelAndJoin()
            }
        }
        pollingJob = null
        sqlPollStatement.reset()
    }

    override suspend fun hasNext(): Boolean {
        return running
    }

    override suspend fun next(): ResultSet {
        return resultsChannel!!.receive()
    }

    companion object {

        @JvmStatic
        private val log = logger()

    }
}
