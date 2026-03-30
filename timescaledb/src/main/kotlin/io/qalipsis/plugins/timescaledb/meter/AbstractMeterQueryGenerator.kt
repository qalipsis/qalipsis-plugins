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

import io.qalipsis.api.query.QueryClause
import io.qalipsis.api.report.DataField
import io.qalipsis.api.report.DataFieldType
import io.qalipsis.plugins.timescaledb.dataprovider.AbstractQueryGenerator
import io.qalipsis.plugins.timescaledb.dataprovider.DataType
import io.qalipsis.plugins.timescaledb.dataprovider.PreparedQueries
import io.qalipsis.plugins.timescaledb.dataprovider.SerializableBoundParameter
import java.util.concurrent.TimeUnit

internal abstract class AbstractMeterQueryGenerator() : AbstractQueryGenerator(
    dataType = DataType.METER,
    databaseTable = "meters",
    queryFields = FIELDS,
    numericFields = FIELDS.filter { it.type == DataFieldType.NUMBER }.map { it.name }.toSet(),
    booleanFields = FIELDS.filter { it.type == DataFieldType.BOOLEAN }.map { it.name }.toSet(),
    jsonFields = emptySet()
) {

    override fun addClauses(
        queryClauses: Collection<QueryClause>,
        sql: StringBuilder,
        boundParametersCollector: (key: String, SerializableBoundParameter) -> Unit,
        nextIdentifierIndexSupplier: () -> Int
    ) {
        val nextIdentifierIndex =
            Regex("(?<=\\$)[0-9]+").findAll(sql.toString()).lastOrNull()?.value?.toIntOrNull() ?: 0
        sql.append(" AND meters.tags->>'scope' = $${nextIdentifierIndex + 1}")

        super.addClauses(queryClauses, sql, boundParametersCollector, nextIdentifierIndexSupplier)
    }

    override fun addDefaultParametersForAggregationStatement(
        tenant: String?,
        timeframeMillis: Long?,
        preparedQueries: PreparedQueries
    ) {
        super.addDefaultParametersForAggregationStatement(tenant, timeframeMillis, preparedQueries)

        val nextAvailableIndex = preparedQueries.nextAvailableAggregationParameterIdentifierIndex
        preparedQueries.bindAggregationParameter(
            ":scope",
            SerializableBoundParameter(
                serializedValue = "period",
                SerializableBoundParameter.Type.STRING,
                "$${nextAvailableIndex}"
            )
        )
    }

    override fun addDefaultParametersForCountAndRetrievalStatement(
        tenant: String?,
        preparedQueries: PreparedQueries
    ) {
        super.addDefaultParametersForCountAndRetrievalStatement(tenant, preparedQueries)

        val nextAvailableIndex = preparedQueries.nextAvailableRetrievalParameterIdentifierIndex
        preparedQueries.bindCountAndRetrievalParameter(
            ":scope",
            SerializableBoundParameter(
                serializedValue = "period",
                SerializableBoundParameter.Type.STRING,
                "$${nextAvailableIndex}"
            )
        )
    }

    companion object {
        /**
         * List of fields that can be used for aggregation and filters.
         */
        val FIELDS = listOf(
            DataField("count", DataFieldType.NUMBER),
            DataField("value", DataFieldType.NUMBER),
            DataField("sum", DataFieldType.NUMBER),
            DataField("mean", DataFieldType.NUMBER),
            DataField("active_tasks", DataFieldType.NUMBER),
            DataField("duration_nano", DataFieldType.NUMBER, TimeUnit.NANOSECONDS.toString()),
            DataField("max", DataFieldType.NUMBER),
            DataField("other", DataFieldType.OBJECT)
        ).sortedBy { it.name }

    }

}