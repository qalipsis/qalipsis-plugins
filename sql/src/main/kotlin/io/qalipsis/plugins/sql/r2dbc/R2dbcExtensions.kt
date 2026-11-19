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
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitLast
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Coroutine-friendly helpers for R2DBC connections.
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
 * Acquires a connection from the pool, suspending until one is available.
 */
suspend fun ConnectionPool.acquireConnection(): Connection {
    return Mono.from(this.create()).awaitFirst()
}

/**
 * Closes the connection, suspending until complete.
 */
suspend fun Connection.closeConnection() {
    Mono.from(this.close()).awaitFirstOrNull()
}

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
suspend fun Connection.executePreparedQuery(sql: String, params: List<Any?>): SqlResultSet {
    val statement = this.createStatement(sql)
    statement.bindParams(params)

    val result = Flux.from(statement.execute()).awaitFirst()

    var columnNames: List<String>? = null
    var columnIndex: Map<String, Int>? = null
    val rows = Flux.from(result.map { row: Row, metadata: RowMetadata ->
        if (columnNames == null) {
            columnNames = metadata.columnMetadatas.map { it.name.lowercase() }
            columnIndex = columnNames!!.withIndex().associate { (index, name) -> name to index }
        }
        val names = columnNames!!
        val values = names.map { name -> normalizeValue(row.get(name)) }
        SqlRow(names, values, columnIndex!!)
    }).collectList().awaitFirst()

    return SqlResultSet(columnNames ?: emptyList(), rows)
}

/**
 * Executes a DML statement (INSERT/UPDATE/DELETE) and returns the number of rows affected.
 */
suspend fun Connection.executeUpdate(sql: String, params: List<Any?>): Long {
    val statement = this.createStatement(sql)
    statement.bindParams(params)
    val result = Flux.from(statement.execute()).awaitFirst()
    return Mono.from(result.getRowsUpdated()).awaitFirst()
}

/**
 * Outcome of a single prepared INSERT execution: either a generated id (possibly `null` when no
 * value is returned by the driver) or the [error] that prevented the insert.
 */
data class SqlInsertOutcome(val id: Long?, val error: Throwable?)

/**
 * Executes a prepared INSERT for each set of [params] and returns the per-record outcome,
 * preserving the original cause when an insert fails so callers can surface it as a step error.
 */
suspend fun Connection.executePreparedInsert(sql: String, params: List<List<Any?>>): List<SqlInsertOutcome> {
    return Flux.fromIterable(params)
        .concatMap { queryParams ->
            Flux.defer {
                val statement = createStatement(sql).returnGeneratedValues()
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
        .awaitLast()
}
