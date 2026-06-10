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

package io.qalipsis.plugins.timescaledb.meter

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.qalipsis.api.logging.LoggerHelper.logger
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.spi.Connection
import java.time.Duration
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingleOrNull
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * R2DBC-based repository for the [meter_name_stats] cache table, which stores the known
 * non-null fields and distinct tag key/values for each (tenant, meter name) pair.
 *
 * @author Eric Jessé
 */
internal class MeterNameStatsRepository(
    private val connectionPool: ConnectionPool,
    private val databaseSchema: String,
    private val objectMapper: ObjectMapper,
) {

    /**
     * Returns the field names that are known to have non-null values for the given meter,
     * or an empty set when no cache entry exists yet.
     */
    suspend fun findFields(tenant: String, name: String): Set<String> {
        val sql =
            "SELECT fields FROM ${databaseSchema}.meter_name_stats WHERE tenant = $1 AND name = $2"
        return Flux.usingWhen(
            connectionPool.create(),
            { connection ->
                Flux.from(
                    connection.createStatement(sql)
                        .bind("$1", tenant)
                        .bind("$2", name)
                        .execute()
                ).flatMap { result ->
                    result.map { row, _ ->
                        row.get("fields", String::class.java)
                    }
                }
            },
            Connection::close
        ).awaitFirstOrNull()
            ?.let { objectMapper.readValue(it, object : TypeReference<List<String>>() {}).toSet() }
            .orEmpty()
            .also { fields -> log.trace { "Found ${fields.size} cached fields for meter $name in tenant $tenant" } }
    }

    /**
     * Returns the known tag keys and their distinct values for the given meter,
     * or an empty map when no cache entry exists yet.
     */
    suspend fun findTags(tenant: String, name: String): Map<String, List<String>> {
        val sql =
            "SELECT tags FROM ${databaseSchema}.meter_name_stats WHERE tenant = $1 AND name = $2"
        return Flux.usingWhen(
            connectionPool.create(),
            { connection ->
                Flux.from(
                    connection.createStatement(sql)
                        .bind("$1", tenant)
                        .bind("$2", name)
                        .execute()
                ).flatMap { result ->
                    result.map { row, _ ->
                        row.get("tags", String::class.java)
                    }
                }
            },
            Connection::close
        ).awaitFirstOrNull()
            ?.let { objectMapper.readValue(it, object : TypeReference<Map<String, List<String>>>() {}) }
            .orEmpty()
            .also { tags -> log.trace { "Found ${tags.size} cached tag keys for meter $name in tenant $tenant" } }
    }

    /**
     * Returns a chunk of (tenant, name) pairs to refresh, starting with entries not yet in
     * the stats table, then existing entries older than [minAge], ordered oldest-first.
     *
     * @param limit maximum number of pairs to return
     * @param minAge minimum age an existing stats entry must have before it is eligible for refresh
     */
    suspend fun findEntriesRequiringRefresh(limit: Int, minAge: Duration): List<Pair<String, String>> {
        val minAgeMs = minAge.toMillis()
        // New meter names not yet in the stats table take priority over stale existing entries.
        // Existing entries are eligible when new meter data has arrived since the last refresh,
        // or when the entry is older than minAge (periodic forced refresh).
        val sql = """
            (
                SELECT DISTINCT m.tenant, m.name
                FROM ${databaseSchema}.meters m
                WHERE NOT EXISTS (
                    SELECT 1 FROM ${databaseSchema}.meter_name_stats s WHERE s.tenant = m.tenant AND s.name = m.name
                )
                LIMIT $limit
            )
            UNION ALL
            (
                SELECT s.tenant, s.name
                FROM ${databaseSchema}.meter_name_stats s
                WHERE EXISTS (
                    SELECT 1 FROM ${databaseSchema}.meters m
                    WHERE m.tenant = s.tenant AND m.name = s.name AND m.timestamp >= s.last_updated
                )
                   OR s.last_updated < NOW() - INTERVAL '$minAgeMs milliseconds'
                ORDER BY s.last_updated ASC
                LIMIT $limit
            )
            LIMIT $limit
        """.trimIndent()
        return Flux.usingWhen(
            connectionPool.create(),
            { connection ->
                Flux.from(connection.createStatement(sql).execute()).flatMap { result ->
                    result.map { row, _ ->
                        (row.get("tenant", String::class.java) ?: "") to
                                (row.get("name", String::class.java) ?: "")
                    }
                }
            },
            Connection::close
        ).collectList().awaitSingleOrNull().orEmpty()
            .also { log.debug { "Found ${it.size} meter name entries requiring refresh" } }
    }

    /**
     * Upserts the fields and tags for a given (tenant, name) pair.
     */
    suspend fun upsert(
        tenant: String,
        name: String,
        fields: Set<String>,
        tags: Map<String, List<String>>,
    ) {
        val fieldsJson = objectMapper.writeValueAsString(fields.sorted())
        val tagsJson = objectMapper.writeValueAsString(tags)
        val sql = """
            INSERT INTO ${databaseSchema}.meter_name_stats (tenant, name, fields, tags, last_updated)
            VALUES ($1, $2, $3::jsonb, $4::jsonb, NOW())
            ON CONFLICT (tenant, name) DO UPDATE
            SET fields = EXCLUDED.fields, tags = EXCLUDED.tags, last_updated = EXCLUDED.last_updated
        """.trimIndent()
        Mono.usingWhen(
            connectionPool.create(),
            { connection ->
                Mono.from(
                    connection.createStatement(sql)
                        .bind("$1", tenant)
                        .bind("$2", name)
                        .bind("$3", fieldsJson)
                        .bind("$4", tagsJson)
                        .execute()
                ).flatMap { result ->
                    Mono.from(result.rowsUpdated)
                }
            },
            Connection::close
        ).awaitSingleOrNull()
            .also { log.trace { "Upserted stats for meter $name in tenant $tenant" } }
    }

    private companion object {
        val log = logger()
    }
}
