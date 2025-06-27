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