/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.plugins.sql.poll

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.sql.dialect.Dialect
import io.qalipsis.plugins.sql.r2dbc.executePreparedQuery
import io.qalipsis.plugins.sql.r2dbc.executeUpdate
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.r2dbc.pool.ConnectionPool
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.extension.RegisterExtension

/**
 *
 * @author Eric Jessé
 */
internal abstract class AbstractSqlIntegrationTest(
    protected val dialect: Dialect,
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
        statements.forEach {
            try {
                val rowsUpdated = connectionPool.executeUpdate(it, emptyList())
                assertThat(rowsUpdated).isEqualTo(1L)
            } catch (e: Exception) {
                log.error(e) { "Error in the statement '$it': ${e.message}" }
                throw e
            }
        }
    }

    protected suspend fun count(table: String): Int {
        val result = connectionPool.executePreparedQuery("select count(*) from ${dialect.quote(table)}", emptyList())
        return (result[0][0] as Number).toInt()
    }

    protected suspend fun sendQuery(sql: String) {
        connectionPool.executePreparedQuery(sql, emptyList())
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
