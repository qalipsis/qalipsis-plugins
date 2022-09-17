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

package io.qalipsis.plugins.influxdb.search

import com.influxdb.query.FluxRecord
import io.qalipsis.plugins.influxdb.poll.InfluxDbQueryMeters

/**
 * A wrapper for the input for search, meters and documents.
 *
 * @property input input value used to generate the search query
 * @property results result of search query procedure in InfluxDb
 * @property meters meters of the query
 *
 * @author Eric Jessé
 */
class InfluxDbSearchResult<I>(
    val input: I,
    val results: List<FluxRecord>,
    val meters: InfluxDbQueryMeters
)
