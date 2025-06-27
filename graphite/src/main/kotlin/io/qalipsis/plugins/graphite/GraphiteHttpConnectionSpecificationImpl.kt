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

package io.qalipsis.plugins.graphite

import io.qalipsis.plugins.graphite.search.GraphiteHttpConnectionSpecification

/**
 * Implementation of [GraphiteHttpConnectionSpecification].
 */
internal class GraphiteHttpConnectionSpecificationImpl : GraphiteHttpConnectionSpecification {

    var url: String = "http://127.0.0.1:8086"

    var username: String = ""

    var password: String = ""
    override fun server(url: String) {
        this.url = url
    }

    override fun basicAuthentication(username: String, password: String) {
        this.username = username
        this.password = password
    }
}