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

package io.qalipsis.plugins.jackson

import com.fasterxml.jackson.databind.ObjectReader
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.steps.datasource.DatasourceIterativeReader
import java.io.InputStreamReader

/**
 * Implementation of a [DatasourceIterativeReader] for Jackson.
 *
 * @author Eric Jessé
 */
internal class JacksonDatasourceIterativeReader<R>(
        private val inputStreamReader: InputStreamReader,
        private val objectReader: ObjectReader
) : DatasourceIterativeReader<R> {

    lateinit var iterator: Iterator<R>

    override fun start(context: StepStartStopContext) {
        iterator = objectReader.readValues(inputStreamReader)
    }

    override suspend fun hasNext() = iterator.hasNext()

    override suspend fun next() = iterator.next()

}
