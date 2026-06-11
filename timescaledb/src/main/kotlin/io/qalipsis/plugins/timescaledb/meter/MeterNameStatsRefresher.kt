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

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.annotation.Scheduled
import io.qalipsis.api.logging.LoggerHelper.logger
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.spi.Connection
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import reactor.core.publisher.Flux

/**
 * Periodically refreshes the [MeterNameStatsRepository] cache, processing the oldest chunk of entries first.
 * For each (tenant, name) pair it computes the set of non-null field names and the distinct tag keys/values
 * observed in the `meters` table, then upserts them into `meter_name_stats`.
 *
 * @author Eric Jessé
 */
@Singleton
@Requirements(
    Requires(env = ["standalone", "head"]),
    Requires(beans = [TimescaledbMeterDataProviderConfiguration::class]),
    Requires(property = "meters.provider.timescaledb.metadata.enabled", notEquals = "false")
)
internal class MeterNameStatsRefresher(
    @Named("meter-data-provider") private val connectionPool: ConnectionPool,
    configuration: TimescaledbMeterDataProviderConfiguration,
    private val statsRepository: MeterNameStatsRepository,
    private val metadataConfiguration: TimescaledbMeterMetadataConfiguration,
) {

    private val databaseSchema: String = configuration.schema

    @Scheduled(
        fixedDelay = "\${meters.provider.timescaledb.metadata.refresh-delay:PT5M}",
        initialDelay = "\${meters.provider.timescaledb.metadata.initial-delay:PT1M}"
    )
    fun refresh() {
        runBlocking {
            try {
                val entries = statsRepository.findEntriesRequiringRefresh(
                    metadataConfiguration.chunkSize,
                    metadataConfiguration.minAge
                )
                log.debug { "Refreshing meter name stats for ${entries.size} entries" }
                entries.forEach { (tenant, name) ->
                    try {
                        val fields = computeNonNullFields(tenant, name)
                        val tags = computeDistinctTags(tenant, name)
                        statsRepository.upsert(tenant, name, fields, tags)
                    } catch (e: Exception) {
                        log.warn(e) { "Failed to refresh stats for meter '$name' of tenant '$tenant'" }
                    }
                }
            } catch (e: Exception) {
                log.error(e) { "Failed to refresh meter name stats" }
            }
        }
    }

    /**
     * Returns the set of field names that have at least one non-null value for the given meter.
     * Includes direct SQL columns (count, value, sum, mean, max) and all keys found in the `other`
     * JSONB column (e.g. active_tasks, duration_nano, percentile_99.9).
     */
    private suspend fun computeNonNullFields(tenant: String, name: String): Set<String> {
        val sql = """
            WITH meter_rows AS (
                SELECT count, value, sum, mean, max, other
                FROM ${databaseSchema}.meters
                WHERE tenant = $1 AND name = $2
            )
            SELECT field_name FROM (
                SELECT 'count'::text AS field_name WHERE EXISTS (SELECT 1 FROM meter_rows WHERE count IS NOT NULL)
                UNION ALL
                SELECT 'value'::text WHERE EXISTS (SELECT 1 FROM meter_rows WHERE value IS NOT NULL)
                UNION ALL
                SELECT 'sum'::text WHERE EXISTS (SELECT 1 FROM meter_rows WHERE sum IS NOT NULL)
                UNION ALL
                SELECT 'mean'::text WHERE EXISTS (SELECT 1 FROM meter_rows WHERE mean IS NOT NULL)
                UNION ALL
                SELECT 'max'::text WHERE EXISTS (SELECT 1 FROM meter_rows WHERE max IS NOT NULL)
                UNION ALL
                SELECT DISTINCT key AS field_name
                FROM meter_rows, jsonb_each_text(other) AS kv(key, val)
                WHERE val IS NOT NULL
            ) AS all_fields
        """.trimIndent()
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
                        row.get("field_name", String::class.java)
                    }
                }
            },
            Connection::close
        ).collectList().awaitSingleOrNull()?.filterNotNull()?.toSet().orEmpty()
    }

    /**
     * Returns the distinct tag keys and their values for the given meter, excluding internal tags.
     */
    private suspend fun computeDistinctTags(tenant: String, name: String): Map<String, List<String>> {
        val sql = """
            SELECT tags.key, STRING_AGG(DISTINCT tags.value, ',' ORDER BY tags.value) AS values
            FROM ${databaseSchema}.meters, lateral jsonb_each_text(tags) AS tags
            WHERE tenant = $1 AND name = $2
              AND tags.key <> ALL (array['dag', 'scope', 'source', 'step', 'previous-step'])
              AND tags.value <> ''
            GROUP BY tags.key
            ORDER BY tags.key
        """.trimIndent()
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
                        val key = row.get("key", String::class.java) ?: return@map null
                        val values = row.get("values", String::class.java)?.split(',').orEmpty()
                        key to values
                    }
                }
            },
            Connection::close
        ).collectList().awaitSingleOrNull()?.filterNotNull()?.toMap().orEmpty()
    }

    private companion object {
        val log = logger()
    }
}
