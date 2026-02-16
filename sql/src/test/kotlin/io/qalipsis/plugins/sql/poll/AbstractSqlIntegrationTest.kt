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

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.sql.r2dbc.acquireConnection
import io.qalipsis.plugins.sql.r2dbc.closeConnection
import io.qalipsis.plugins.sql.r2dbc.executePreparedQuery
import io.qalipsis.plugins.sql.r2dbc.executeUpdate
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.r2dbc.pool.ConnectionPool
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.atomic.AtomicReference

/**
 *
 * @author Eric Jessé
 */
internal abstract class AbstractSqlIntegrationTest(
    private val connectionPoolFactory: () -> ConnectionPool
) {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    protected lateinit var connectionPool: ConnectionPool

    private var initialized = false

    internal open fun setUp() {
        if (!initialized) {
            connectionPool = connectionPoolFactory()
            CONNECTION_POOL.set(connectionPool)
            init()
            initialized = true
        }
    }

    protected open fun init() = Unit

    protected suspend fun execute(statements: List<String>) {
        val connection = connectionPool.acquireConnection()
        try {
            statements.forEach {
                try {
                    val rowsUpdated = connection.executeUpdate(it, emptyList())
                    assertThat(rowsUpdated).isEqualTo(1L)
                } catch (e: Exception) {
                    log.error(e) { "Error in the statement '$it': ${e.message}" }
                    throw e
                }
            }
        } finally {
            connection.closeConnection()
        }
    }

    protected suspend fun count(table: String): Int {
        val connection = connectionPool.acquireConnection()
        try {
            val result = connection.executePreparedQuery("select count(*) from $table", emptyList())
            return (result[0][0] as Number).toInt()
        } finally {
            connection.closeConnection()
        }
    }

    protected suspend fun sendQuery(sql: String) {
        val connection = connectionPool.acquireConnection()
        try {
            connection.executePreparedQuery(sql, emptyList())
        } finally {
            connection.closeConnection()
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()

        @JvmStatic
        private val CONNECTION_POOL = AtomicReference<ConnectionPool>()

        @JvmStatic
        @AfterAll
        fun classTearDown(): Unit = runBlocking {
            CONNECTION_POOL.get().disposeLater().awaitFirstOrNull()
        }
    }
}
