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