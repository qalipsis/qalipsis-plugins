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

package io.qalipsis.plugins.r2dbc.jasync.poll

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jasync.sql.db.SuspendingConnection
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.test.coroutines.TestDispatcherProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.atomic.AtomicReference

/**
 *
 * @author Eric Jessé
 */
internal abstract class AbstractJasyncIntegrationTest(
    private val connectionPoolFactory: () -> SuspendingConnection
) {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    protected lateinit var connection: SuspendingConnection

    private var initialized = false

    internal open fun setUp() {
        if (!initialized) {
            connection = connectionPoolFactory()
            CONNECTION.set(connection)
            init()
            initialized = true
        }
    }

    protected open fun init() = Unit

    protected suspend fun execute(statements: List<String>) {
        connection.inTransaction { conn ->
            statements.forEach {
                try {
                    assertThat(conn.sendQuery(it).rowsAffected).isEqualTo(1)
                } catch (e: Exception) {
                    log.error(e) { "Error in the statement '$it': ${e.message}" }
                    throw e
                }
            }
        }
    }

    protected suspend fun count(table: String) =
        connection.sendQuery("select count(*) from $table").rows[0].getLong(0)!!.toInt()

    companion object {

        @JvmStatic
        private val log = logger()

        @JvmStatic
        private val CONNECTION = AtomicReference<SuspendingConnection>()

        @JvmStatic
        @AfterAll
        fun classTearDown(): Unit = runBlocking {
            CONNECTION.get().disconnect()
        }
    }
}
