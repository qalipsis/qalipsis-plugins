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

package io.qalipsis.plugins.influxdb.poll.converters

import com.influxdb.query.FluxRecord
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.plugins.influxdb.poll.InfluxDbQueryResult
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of [DatasourceObjectConverter], to send individual records to the output.
 *
 * @author Eric Jessé
 */
internal class InfluxDbDocumentPollSingleConverter(
) : DatasourceObjectConverter<InfluxDbQueryResult, FluxRecord> {

    override suspend fun supply(
        offset: AtomicLong,
        value: InfluxDbQueryResult,
        output: StepOutput<FluxRecord>
    ) {
        value.results.forEach {
            tryAndLogOrNull(log) {
                output.send(it)
            }
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
