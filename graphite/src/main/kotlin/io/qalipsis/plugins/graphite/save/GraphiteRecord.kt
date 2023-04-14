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

package io.qalipsis.plugins.graphite.save

import io.qalipsis.plugins.graphite.save.codecs.GraphitePlaintextStringEncoder
import java.time.Instant

/**
 * A wrapper class for converting data to the plaintext.
 *
 * @property metricPath is the metric namespace that you want to populate
 * @property value is the value that you want to assign to the metric at this time
 * @property timestamp is the number of seconds since unix epoch time
 *
 * @author Alexey Prudnikov
 */
data class GraphiteRecord (
    val metricPath: String,
    val value: Any? = null,
    val timestamp: Instant? = null
){
    override fun toString(): String {
        return GraphitePlaintextStringEncoder().convertToPlaintext(this)
    }
}