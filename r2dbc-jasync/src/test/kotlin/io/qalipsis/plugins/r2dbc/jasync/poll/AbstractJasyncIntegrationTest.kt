/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
