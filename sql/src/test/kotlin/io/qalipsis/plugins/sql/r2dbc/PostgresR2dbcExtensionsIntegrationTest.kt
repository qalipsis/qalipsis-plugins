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

package io.qalipsis.plugins.sql.r2dbc

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isLessThan
import io.qalipsis.plugins.sql.SqlConnection
import io.qalipsis.plugins.sql.dialect.DialectConfigurations
import io.r2dbc.pool.ConnectionPool
import java.sql.DriverManager
import java.time.Duration
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainerProvider
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Verifies that concurrent queries sharing a small pool never see each other's results.
 */
@Testcontainers
internal class PostgresR2dbcExtensionsIntegrationTest {

    private lateinit var connectionPool: ConnectionPool

    @BeforeEach
    internal fun setUp() {
        DriverManager.getConnection(db.jdbcUrl, db.username, db.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("drop table if exists orders")
                statement.execute("create table orders (correlation_id varchar(64) primary key, customer_id varchar(64))")
                statement.execute("insert into orders select 'o-' || i, 'c-' || i from generate_series(1, $KEYS_COUNT) as i")
            }
        }
        connectionPool = DialectConfigurations.POSTGRESQL.createConnectionPool(
            SqlConnection(
                host = "localhost",
                port = db.firstMappedPort,
                username = db.username,
                password = db.password,
                database = db.databaseName,
                maxSize = POOL_SIZE
            )
        )
    }

    @AfterEach
    internal fun tearDown(): Unit = runBlocking {
        connectionPool.disposeLater().awaitFirstOrNull()
    }

    @Test
    internal fun `given many concurrent searches on a small pool when executed then each gets exactly its own row`() =
        runBlocking(Dispatchers.Default) {
            val anomalies = (1..SEARCHES_COUNT).map { index ->
                async {
                    val key = index % KEYS_COUNT + 1
                    runCatching {
                        connectionPool.executePreparedQuery(
                            "select customer_id from orders where correlation_id = $1",
                            listOf("o-$key")
                        )
                    }.fold(
                        onSuccess = { resultSet ->
                            val customerIds = resultSet.map { it["customer_id"] }
                            "o-$key -> $customerIds".takeUnless { customerIds == listOf("c-$key") }
                        },
                        onFailure = { "o-$key -> ${it::class.simpleName}: ${it.message}" }
                    )
                }
            }.awaitAll().filterNotNull()

            assertThat(anomalies).isEmpty()
        }

    @Test
    internal fun `given concurrent searches cancelled while running when executed then the other searches get exactly their own row without waiting for the cancelled ones`() =
        runBlocking(Dispatchers.Default) {
            // Limits the in-flight searches to the pool size, so that the timeouts mostly expire while the query runs.
            val inFlight = Semaphore(POOL_SIZE)
            val start = System.nanoTime()
            val anomalies = (1..CANCELLED_SEARCHES_COUNT).map { index ->
                async {
                    val key = index % KEYS_COUNT + 1
                    val cancelled = index % 2 == 0
                    inFlight.withPermit {
                        runCatching {
                            if (cancelled) {
                                withTimeout(Random.nextLong(1, MAX_CANCELLATION_TIMEOUT_MS)) {
                                    connectionPool.executePreparedQuery(SLOW_QUERY, listOf("o-$key", SLOW_QUERY_SECONDS))
                                }
                            } else {
                                connectionPool.executePreparedQuery(SLOW_QUERY, listOf("o-$key", 0.0))
                            }
                        }.fold(
                            onSuccess = { resultSet ->
                                val customerIds = resultSet.map { it["customer_id"] }
                                "o-$key -> $customerIds".takeUnless { !cancelled && customerIds == listOf("c-$key") }
                            },
                            onFailure = {
                                "o-$key -> ${it::class.simpleName}: ${it.message}".takeUnless { _ ->
                                    cancelled && it is TimeoutCancellationException
                                }
                            }
                        )
                    }
                }
            }.awaitAll().filterNotNull()
            val elapsed = Duration.ofNanos(System.nanoTime() - start)

            assertThat(anomalies).isEmpty()
            // Without releasing the connections of the cancelled searches, the slow queries alone would last that long.
            val slowQueriesDuration = Duration.ofMillis(
                (CANCELLED_SEARCHES_COUNT / 2 * SLOW_QUERY_SECONDS * 1000 / POOL_SIZE).toLong()
            )
            assertThat(elapsed).isLessThan(slowQueriesDuration.dividedBy(2))
        }

    private companion object {

        const val POOL_SIZE = 4

        const val KEYS_COUNT = 500

        const val SEARCHES_COUNT = 20_000

        const val CANCELLED_SEARCHES_COUNT = 2_000

        const val SLOW_QUERY = "select customer_id from orders, pg_sleep($2) where correlation_id = $1"

        const val SLOW_QUERY_SECONDS = 0.2

        const val MAX_CANCELLATION_TIMEOUT_MS = 30L

        @Container
        @JvmStatic
        val db = PostgreSQLContainerProvider().newInstance()
    }
}
