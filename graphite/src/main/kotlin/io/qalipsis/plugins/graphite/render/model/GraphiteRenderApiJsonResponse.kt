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

package io.qalipsis.plugins.graphite.render.model

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * A wrapper class for response received from Graphite server.
 *
 * @property target received target
 * @property tags a map of received tags
 * @property dataPoints list of received values and timestamps wrapped as [GraphiteRenderApiJsonPairResponse]
 *
 * @author rklymenko
 */
data class GraphiteRenderApiJsonResponse(
    val target: String,
    val tags: Map<String, String>,
    @JsonProperty("datapoints")
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    val dataPoints: List<GraphiteRenderApiJsonPairResponse>
)