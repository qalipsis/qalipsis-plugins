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

import io.qalipsis.plugins.sql.SqlResultSet
import io.qalipsis.plugins.sql.SqlRow
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.spi.Connection
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import io.r2dbc.spi.Statement
import io.r2dbc.spi.Wrapped
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import kotlinx.coroutines.reactive.awaitSingle
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Coroutine-friendly helpers to execute statements with connections of an R2DBC [ConnectionPool].
 *
 * Each helper acquires a connection, executes the statement, consumes all its results and releases the connection
 * in a single reactive pipeline, which is awaited only once and without cancellation.
 * Bridging each of these stages separately to coroutines is not safe under concurrency:
 * - `awaitFirst` cancels the publisher after its first value; r2dbc-pool releases a connection when the acquisition
 *   is cancelled, even after it was emitted, and the cancellation of `Statement.execute` makes the drivers discard
 *   or close the pending conversation,
 * - releasing the connection from the coroutine races with the completion of the conversation in the driver.
 * Both let concurrent queries share a connection, which crosses their results or corrupts the protocol state.
 *
 * When the awaiting coroutine is cancelled (e.g. a step timeout) while the statement runs, the physical connection
 * is closed, so that the pool discards it instead of reusing a connection still busy with the cancelled statement.
 *
 * @author Eric Jessé
 */

/**
 * Normalizes temporal types returned by different R2DBC drivers.
 * Some drivers (e.g. r2dbc-mysql) return [ZonedDateTime] for DATETIME columns,
 * while others (e.g. r2dbc-postgresql, r2dbc-mariadb) return [java.time.LocalDateTime].
 * This ensures consistent types across all drivers.
 */
private fun normalizeValue(value: Any?): Any? = when (value) {
    is ZonedDateTime -> value.toLocalDateTime()
    is OffsetDateTime -> value.toLocalDateTime()
    else -> value
}

/**
 * Executes [block] with a connection of the pool, released once the publisher of [block] is terminated.
 */
private suspend fun <T : Any> ConnectionPool.withConnection(block: (Connection) -> Mono<T>): T =
    Mono.usingWhen(create(), block, Connection::close, { connection, _ -> connection.close() }, ::discard)
        .awaitSingle()

/**
 * Closes the physical connection of the pooled [connection] before releasing it, which lets the pool destroy it
 * (see [io.qalipsis.plugins.sql.dialect.DialectConfigurations]).
 */
private fun discard(connection: Connection): Mono<Void> =
    Mono.justOrEmpty<Connection>((connection as? Wrapped<*>)?.unwrap() as? Connection)
        .flatMap { physicalConnection -> Mono.from(physicalConnection.close()) }
        .onErrorResume { Mono.empty() }
        .then(Mono.from(connection.close()))

/**
 * Binds parameters to a statement, handling nulls.
 */
private fun Statement.bindParams(params: List<Any?>) {
    params.forEachIndexed { index, value ->
        if (value == null) {
            this.bindNull(index, Any::class.java)
        } else {
            this.bind(index, value)
        }
    }
}

/**
 * Executes a prepared query and drains the reactive result into a [SqlResultSet].
 */
suspend fun ConnectionPool.executePreparedQuery(sql: String, params: List<Any?>): SqlResultSet =
    withConnection { connection ->
        val statement = connection.createStatement(sql)
        statement.bindParams(params)

        var columnNames: List<String>? = null
        var columnIndex: Map<String, Int>? = null
        Flux.from(statement.execute())
            .concatMap { result ->
                result.map { row: Row, metadata: RowMetadata ->
                    if (columnNames == null) {
                        columnNames = metadata.columnMetadatas.map { it.name.lowercase() }
                        columnIndex = columnNames!!.withIndex().associate { (index, name) -> name to index }
                    }
                    val names = columnNames!!
                    val values = names.map { name -> normalizeValue(row.get(name)) }
                    SqlRow(names, values, columnIndex!!)
                }
            }
            .collectList()
            .map { rows -> SqlResultSet(columnNames ?: emptyList(), rows) }
    }

/**
 * Executes a DML statement (INSERT/UPDATE/DELETE) and returns the number of rows affected.
 */
suspend fun ConnectionPool.executeUpdate(sql: String, params: List<Any?>): Long =
    withConnection { connection ->
        val statement = connection.createStatement(sql)
        statement.bindParams(params)
        Flux.from(statement.execute())
            .concatMap { result -> result.rowsUpdated }
            .reduce(0L, Long::plus)
    }

/**
 * Outcome of a single prepared INSERT execution: either a generated id (possibly `null` when no
 * value is returned by the driver) or the [error] that prevented the insert.
 */
data class SqlInsertOutcome(val id: Long?, val error: Throwable?)

/**
 * Executes a prepared INSERT for each set of [params] with the same connection and returns the per-record outcome,
 * preserving the original cause when an insert fails so callers can surface it as a step error.
 */
suspend fun ConnectionPool.executePreparedInsert(sql: String, params: List<List<Any?>>): List<SqlInsertOutcome> =
    withConnection { connection ->
        Flux.fromIterable(params)
            .concatMap { queryParams ->
                Flux.defer {
                    val statement = connection.createStatement(sql).returnGeneratedValues()
                    statement.bindParams(queryParams)
                    Flux.from(statement.execute())
                        .concatMap { result ->
                            result.map { row ->
                                val value = row.get(0)
                                SqlInsertOutcome(if (value is Number) value.toLong() else 0L, null)
                            }
                        }
                }.onErrorResume { error -> Flux.just(SqlInsertOutcome(null, error)) }
            }
            .collectList()
    }
