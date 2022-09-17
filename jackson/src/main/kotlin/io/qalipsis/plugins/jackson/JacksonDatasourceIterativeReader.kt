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
