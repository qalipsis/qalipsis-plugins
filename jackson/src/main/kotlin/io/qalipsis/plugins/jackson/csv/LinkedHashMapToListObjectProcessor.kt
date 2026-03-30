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

package io.qalipsis.plugins.jackson.csv

import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.datasource.DatasourceException
import io.qalipsis.api.steps.datasource.DatasourceObjectProcessor
import java.util.concurrent.atomic.AtomicLong

/**
 *
 * @author Eric Jessé
 */
class LinkedHashMapToListObjectProcessor(
        private val conversionsRules: List<((Any?) -> Any?)?>
) : DatasourceObjectProcessor<LinkedHashMap<String, Any?>, List<Any?>> {

    override fun process(offset: AtomicLong, readObject: LinkedHashMap<String, Any?>): List<Any?> {
        val errors = mutableListOf<String>()
        val result = arrayListOf<Any?>()
        readObject.entries.forEachIndexed { columnIndex, entry ->
            val value = entry.value
            try {
                result.add(
                        if (conversionsRules.size > columnIndex && conversionsRules[columnIndex] != null) {
                            conversionsRules[columnIndex]?.let { it(value) } ?: value
                        } else {
                            value
                        }
                )
            } catch (e: Exception) {
                log.debug(e) { "Row ${offset.get()}, column $columnIndex, value $value: ${e.message}" }
                errors.add("column $columnIndex, value $value: ${e.message}")
            }
        }
        if (errors.isNotEmpty()) {
            throw DatasourceException(errors.joinToString())
        }
        return result
    }

    companion object {

        @JvmStatic
        private val log = logger()

    }
}
