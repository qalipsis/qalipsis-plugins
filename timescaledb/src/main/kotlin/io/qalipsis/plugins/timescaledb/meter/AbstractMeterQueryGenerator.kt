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
import io.qalipsis.api.query.QueryDescription
import io.qalipsis.api.report.DataField
import io.qalipsis.api.report.DataFieldType
import io.qalipsis.plugins.timescaledb.dataprovider.AbstractQueryGenerator
import io.qalipsis.plugins.timescaledb.dataprovider.DataType
import io.qalipsis.plugins.timescaledb.dataprovider.SerializableBoundParameter
import java.util.concurrent.TimeUnit

internal abstract class AbstractMeterQueryGenerator : AbstractQueryGenerator(
    dataType = DataType.METER,
    databaseTable = "meters",
    queryFields = FIELDS,
    numericFields = FIELDS.filter { it.type == DataFieldType.NUMBER }.map { it.name }.toSet(),
    booleanFields = FIELDS.filter { it.type == DataFieldType.BOOLEAN }.map { it.name }.toSet(),
    jsonFields = emptySet()
) {

    override fun isValidFieldName(fieldName: String): Boolean =
        super.isValidFieldName(fieldName) || isOtherField(fieldName)

    override fun isNumericFieldName(fieldName: String?): Boolean =
        super.isNumericFieldName(fieldName) || (fieldName != null && isOtherField(fieldName))

    override fun resolveParameterType(fieldName: String?, value: String): SerializableBoundParameter.Type =
        if (fieldName != null && isOtherField(fieldName)) {
            if (value.contains(',')) SerializableBoundParameter.Type.NUMBER_ARRAY else SerializableBoundParameter.Type.NUMBER
        } else {
            super.resolveParameterType(fieldName, value)
        }

    override fun appendFieldNotNullFilter(sql: StringBuilder, fieldName: String) {
        if (isOtherField(fieldName)) {
            sql.append(" AND meters.other->>'$fieldName' IS NOT NULL")
        } else {
            super.appendFieldNotNullFilter(sql, fieldName)
        }
    }

    override fun addClauses(
        queryClauses: Collection<QueryClause>,
        sql: StringBuilder,
        boundParametersCollector: (key: String, SerializableBoundParameter) -> Unit,
        nextIdentifierIndexSupplier: () -> Int
    ) {
        // Scope is stored in the tags JSON field; return period-level and campaign-level meters.
        sql.append(" AND meters.tags->>'scope' IN ('period', 'campaign')")
        sql.append(" %s") // Placeholder for additional filters (specific campaigns or scenarios)
        queryClauses.forEach { clause ->
            when {
                isOtherField(clause.name) -> {
                    sql.append(""" AND (meters.other->>'${clause.name}')::decimal""")
                    sql.append(
                        """ ${
                            convertComparator(
                                clause.name,
                                clause.operator,
                                clause.value,
                                boundParametersCollector,
                                nextIdentifierIndexSupplier()
                            )
                        }"""
                    )
                }

                clause.name == "name" || clause.name in SQL_COLUMN_FIELDS -> {
                    sql.append(""" AND meters.${clause.name}""")
                    sql.append(
                        """ ${
                            convertComparator(
                                clause.name,
                                clause.operator,
                                clause.value,
                                boundParametersCollector,
                                nextIdentifierIndexSupplier()
                            )
                        }"""
                    )
                }

                else -> {
                    val tagKey = when {
                        clause.name.startsWith("tag.") -> clause.name.substringAfter("tag.")
                        clause.name.startsWith("tags.") -> clause.name.substringAfter("tags.")
                        else -> clause.name
                    }
                    sql.append(""" AND meters.tags->>'${tagKey}'""")
                    sql.append(
                        """ ${
                            convertComparator(
                                null,
                                clause.operator,
                                clause.value,
                                boundParametersCollector,
                                nextIdentifierIndexSupplier()
                            )
                        }"""
                    )
                }
            }
        }
    }

    override fun buildRootQueryForAggregation(
        query: QueryDescription,
        boundParameters: Map<String, SerializableBoundParameter>,
    ): StringBuilder {
        val schemaIdentifier = boundParameters[":schema"]!!.identifiers.first()
        val result = when {
            query.fieldName == null -> "NULL::decimal"
            isOtherField(query.fieldName!!) -> "(meters.other->>'${query.fieldName}')::decimal"
            else -> "meters.${query.fieldName}"
        }
        return StringBuilder("SELECT meters.timestamp AS bucket, $result AS result, meters.campaign AS campaign, meters.tags->>'scope' AS scope FROM ${schemaIdentifier}.meters")
    }

    override fun appendGroupingForAggregation(sql: StringBuilder) {
        // Meters are not aggregated; raw records are returned as-is.
    }

    override fun appendOrderingForAggregation(sql: StringBuilder) {
        sql.append(" ORDER BY meters.campaign, meters.timestamp")
    }

    private fun isOtherField(name: String): Boolean = name in OTHER_FIELDS || name.startsWith("percentile_")

    companion object {
        /**
         * Direct SQL columns on the meters table (not stored in the `other` JSONB).
         */
        val SQL_COLUMN_FIELDS = setOf("count", "value", "sum", "mean", "max")

        /**
         * Fixed field names stored as keys inside the `other` JSONB column.
         * Percentile fields (percentile_*) are also stored there but matched by prefix.
         */
        private val OTHER_FIELDS = setOf("active_tasks", "duration_nano")

        /**
         * List of fields that can be used for aggregation and filters.
         * Fields not in [SQL_COLUMN_FIELDS] (active_tasks, duration_nano, percentile_*) are stored in the `other` JSONB.
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