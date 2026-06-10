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

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.validation.Validated
import io.qalipsis.api.report.DataField
import io.qalipsis.api.report.MeterMetadataProvider
import io.qalipsis.plugins.timescaledb.dataprovider.AbstractDataProvider
import io.r2dbc.pool.ConnectionPool
import jakarta.inject.Named
import jakarta.inject.Singleton

/**
 * Implementation of [AbstractDataProvider] for the meters in TimescaleDB.
 *
 * @author Eric Jessé
 */
@Singleton
@Requirements(
    Requires(env = ["standalone", "head"]),
    Requires(beans = [TimescaledbMeterDataProviderConfiguration::class])
)
@Validated
internal class TimescaledbMeterDataProvider(
    @Named("meter-data-provider") connectionPool: ConnectionPool,
    configuration: TimescaledbMeterDataProviderConfiguration,
    meterQueryGenerator: AbstractMeterQueryGenerator,
    objectMapper: ObjectMapper,
    private val statsRepository: MeterNameStatsRepository,
) : AbstractDataProvider(
    connectionPool = connectionPool,
    databaseSchema = configuration.schema,
    databaseTable = "meters",
    queryGenerator = meterQueryGenerator,
    objectMapper = objectMapper,
    excludedTags = setOf("minion", "dag")
), MeterMetadataProvider {

    /**
     * Returns the fields known to carry values for the given meter name when a cache entry exists,
     * falling back to the full list of meter fields when no entry is found.
     */
    override suspend fun listFields(tenant: String, name: String?): Collection<DataField> {
        if (name != null) {
            val cachedFieldNames = statsRepository.findFields(tenant, name)
            if (cachedFieldNames.isNotEmpty()) {
                return AbstractMeterQueryGenerator.FIELDS.filter { it.name in cachedFieldNames }
            }
        }
        return AbstractMeterQueryGenerator.FIELDS
    }

    /**
     * Returns tag keys and distinct values for the given meter name from the cache when available,
     * falling back to a live database query otherwise.
     */
    override suspend fun searchTagsAndValues(
        tenant: String,
        name: String?,
        filters: Collection<String>,
        size: Int,
    ): Map<String, Collection<String>> {
        if (name != null) {
            val cachedTags = statsRepository.findTags(tenant, name)
            if (cachedTags.isNotEmpty()) {
                return filterCachedTags(cachedTags, filters, size)
            }
        }
        return super.searchTagsAndValues(tenant, name, filters, size)
    }

    private fun filterCachedTags(
        tags: Map<String, List<String>>,
        filters: Collection<String>,
        size: Int,
    ): Map<String, Collection<String>> {
        if (filters.isEmpty()) {
            return tags.entries.take(size).associate { (k, v) -> k to v }
        }
        val patterns = filters.map { it.replace('*', '%').replace('?', '_').lowercase() }
        return tags
            .filter { (key, values) ->
                patterns.any { pat ->
                    matchesPattern(key.lowercase(), pat) || values.any { matchesPattern(it.lowercase(), pat) }
                }
            }
            .entries
            .take(size)
            .associate { (k, v) -> k to v }
    }

    private fun matchesPattern(value: String, pattern: String): Boolean {
        if (!pattern.contains('%') && !pattern.contains('_')) return value.contains(pattern)
        val regex = Regex("^" + pattern.split('%').joinToString(".*") { part ->
            part.split('_').joinToString(".") { Regex.escape(it) }
        } + "$")
        return regex.matches(value)
    }
}
