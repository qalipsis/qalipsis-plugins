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

package io.qalipsis.plugins.timescaledb.dataprovider

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.validation.Validated
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.query.QueryDescription
import io.qalipsis.api.report.DataField
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.spi.Connection
import kotlinx.coroutines.reactor.awaitSingleOrNull
import reactor.core.publisher.Flux

@Validated
internal abstract class AbstractDataProvider(
    private val connectionPool: ConnectionPool,
    private val databaseSchema: String,
    private val databaseTable: String,
    private val queryGenerator: AbstractQueryGenerator,
    private val objectMapper: ObjectMapper,
    excludedTags: Set<String> = emptySet()
) {

    private val excludedTagsArray = excludedTags.toTypedArray()

    suspend fun createQuery(tenant: String?, query: QueryDescription): String {
        return objectMapper.writeValueAsString(queryGenerator.prepareQueries(tenant, query))
    }

    suspend fun listFields(tenant: String, name: String?): Collection<DataField> {
        return queryGenerator.queryFields
    }

    suspend fun searchNames(
        tenant: String,
        campaignKey: String?,
        filters: Collection<String>,
        size: Int,
    ): Collection<String> {
        val params = mutableMapOf<String, Any>()
        params["$1"] = tenant
        val sql =
            StringBuilder("""SELECT DISTINCT "name" FROM ${databaseSchema}.${databaseTable} WHERE "tenant" = $1 AND "campaign" IS NOT NULL""")

        var nextBindingIndex = 2
        if (filters.isNotEmpty()) {
            val binding = "\$${nextBindingIndex++}"
            params[binding] = filters.map(this::convertWildcards).toTypedArray()
            sql.append(""" AND "name" ILIKE any (array[$binding])""")
        }
        if (!campaignKey.isNullOrBlank()) {
            val binding = "\$${nextBindingIndex++}"
            params[binding] = campaignKey
            sql.append(""" AND "campaign" = $binding""")
        }
        sql.append(""" ORDER BY "name" LIMIT $size""")
        val query = sql.toString()

        log.debug { "Acquiring a connection" }
        return Flux.usingWhen(
            connectionPool.create()
                .doOnNext { log.debug { "Acquired a connection" } },
            { connection ->
                log.debug { "Executing the SQL query: $query" }
                Flux.from(
                    connection.createStatement(query)
                        .also {
                            params.forEach { (binding, value) ->
                                log.trace { "Binding $binding to $value" }
                                it.bind(binding, value)
                            }
                        }.execute()
                ).flatMap { result ->
                    result.map { row, _ -> row.get("name", String::class.java) }
                }
            },
            Connection::close
        ).collectList().awaitSingleOrNull().orEmpty().also {
            log.debug { "Found ${it.size} names" }
        }
    }

    suspend fun searchTagsAndValues(
        tenant: String,
        name: String?,
        filters: Collection<String>,
        size: Int
    ): Map<String, Collection<String>> {
        val params = mutableMapOf<String, Any>()
        params["$1"] = tenant
        params["$2"] = excludedTagsArray
        val sql =
            StringBuilder(
                """SELECT tags.key AS KEY, STRING_AGG(DISTINCT(tags.value), ',' ORDER BY tags.value) AS value FROM ${databaseSchema}.${databaseTable} AS e, lateral jsonb_each_text(tags) AS tags 
                |WHERE "tenant" = $1 AND "campaign" IS NOT NULL AND tags.value <> ''""".trimMargin()
            )
        sql.append(""" AND tags.key <> all (array[$2])""")

        var nextBindingIndex = 3
        if (!name.isNullOrBlank()) {
            val binding = "\$${nextBindingIndex++}"
            params[binding] = name.trim()
            sql.append(""" AND name = $binding""")
        }
        if (filters.isNotEmpty()) {
            val binding = "\$${nextBindingIndex++}"
            params[binding] = filters.map(this::convertWildcards).toTypedArray()
            sql.append(""" AND (tags.key ILIKE any (array[$binding]) OR tags.value ILIKE any (array[$binding]))""")
        }
        sql.append(""" GROUP BY tags.key ORDER BY tags.key LIMIT $size""")
        val query = sql.toString()

        log.debug { "Acquiring a connection" }
        return Flux.usingWhen(
            connectionPool.create()
                .doOnNext { log.debug { "Acquired a connection" } },
            { connection ->
                log.debug { "Executing the SQL query: $query" }
                Flux.from(
                    connection.createStatement(query)
                        .also {
                            params.forEach { (binding, value) ->
                                log.trace { "Binding $binding to $value" }
                                it.bind(binding, value)
                            }
                        }.execute()
                ).flatMap { result ->
                    result.map { row, _ ->
                        row.get("key", String::class.java)!! to (row.get("value", String::class.java)?.split(',')
                            .orEmpty())
                    }
                }
            },
            Connection::close
        ).collectList().awaitSingleOrNull()?.toMap().orEmpty().also {
            log.debug { "Found ${it.size} tags and values" }
        }
    }

    private fun convertWildcards(clause: String) = clause.replace('*', '%').replace('?', '_')

    private companion object {
        val log = logger()
    }

}