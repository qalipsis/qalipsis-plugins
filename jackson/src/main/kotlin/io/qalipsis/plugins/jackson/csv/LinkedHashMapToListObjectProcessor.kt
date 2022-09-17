/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
