/*
 * Copyright 2024 AERIS IT Solutions GmbH
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

package io.qalipsis.plugins.graphite.search

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * Represents Graphite data points under a single target.
 */
data class DataPoints(
    val target: String,
    val tags: Map<String, String>,
    @JsonProperty("datapoints")
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    val dataPoints: List<DataPoint>
) {

    data class DataPoint(
        val value: Number,
        val timestamp: Instant
    )
}