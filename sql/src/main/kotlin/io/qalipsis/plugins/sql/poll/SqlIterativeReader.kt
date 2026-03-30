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

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.datasource.DatasourceIterativeReader
import io.qalipsis.plugins.sql.SqlResultSet
import io.qalipsis.plugins.sql.dialect.Dialect
import io.qalipsis.plugins.sql.r2dbc.acquireConnection
import io.qalipsis.plugins.sql.r2dbc.closeConnection
import io.qalipsis.plugins.sql.r2dbc.executePreparedQuery
import io.r2dbc.pool.ConnectionPool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import java.time.Duration

/**
 * Database reader based upon R2DBC for MySQL, MariaDB and PostgreSQL.
 *
 * @property connectionPoolFactory supplier for the DB connection pool
 * @property dialect the database dialect to use
 * @property sqlPollStatement statement to execute
 * @property pollDelay duration between the end of a poll and the start of the next one
 * @property resultsChannelFactory factory to create the channel containing the received results sets
 * @property running running state of the reader
 * @property pollingJob instance of the background job polling data from the database
 *
 * @author Eric Jessé
 */
internal class SqlIterativeReader(
    private val ioCoroutineScope: CoroutineScope,
    private val connectionPoolFactory: () -> ConnectionPool,
    private val dialect: Dialect,
    private val sqlPollStatement: SqlPollStatement,
    private val pollDelay: Duration,
    private val resultsChannelFactory: () -> Channel<SqlResultSet>
) : DatasourceIterativeReader<SqlResultSet> {

    private var running = false

    private var pollingJob: Job? = null

    private var resultsChannel: Channel<SqlResultSet>? = null

    private lateinit var connectionPool: ConnectionPool

    private var lastQuery: String? = null

    private var convertedQuery: String? = null

    override fun start(context: StepStartStopContext) {
        init()
        connectionPool = connectionPoolFactory()
        running = true
        pollingJob = ioCoroutineScope.launch {
            try {
                while (running) {
                    poll()
                    if (running) {
                        delay(pollDelay.toMillis())
                    }
                }
            } finally {
                connectionPool.disposeLater().awaitFirstOrNull()
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
     */
    private suspend fun poll() {
        try {
            val connection = connectionPool.acquireConnection()
            try {
                val currentQuery = sqlPollStatement.query
                if (currentQuery != lastQuery) {
                    convertedQuery = dialect.convertPlaceholders(currentQuery)
                    lastQuery = currentQuery
                }
                val result = connection.executePreparedQuery(convertedQuery!!, sqlPollStatement.parameters)
                if (result.isNotEmpty()) {
                    log.debug { "A result set with ${result.size} records(s) was received" }
                    resultsChannel?.send(result)
                    sqlPollStatement.saveTiebreaker(result.last())
                } else {
                    log.debug { "An empty result set was received" }
                }
            } finally {
                connection.closeConnection()
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
        lastQuery = null
        convertedQuery = null
        sqlPollStatement.reset()
    }

    override suspend fun hasNext(): Boolean {
        return running
    }

    override suspend fun next(): SqlResultSet {
        return resultsChannel!!.receive()
    }

    companion object {

        @JvmStatic
        private val log = logger()

    }
}
