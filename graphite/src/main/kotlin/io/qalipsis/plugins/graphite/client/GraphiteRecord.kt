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

package io.qalipsis.plugins.graphite.client

import java.time.Instant

/**
 * A wrapper class for converting data to the plaintext.
 *
 * @property path is the metric namespace that you want to populate
 * @property timestamp the instant when the record occurred
 * @property value is the value that you want to assign to the metric at this time
 * @property tags a collection of tags to qualify the message
 *
 * @author Alexey Prudnikov
 */
data class GraphiteRecord(
    val path: String,
    val timestamp: Instant = Instant.now(),
    val value: Number,
    val tags: Map<String, String> = emptyMap()
) {

    constructor(
        metricPath: String,
        timestamp: Instant = Instant.now(),
        value: Number,
        vararg tags: Pair<String, String>
    ) : this(
        metricPath, timestamp, value, tags.toMap()
    )
}