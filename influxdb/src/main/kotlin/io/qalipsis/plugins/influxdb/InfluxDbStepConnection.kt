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

package io.qalipsis.plugins.influxdb

import javax.validation.constraints.NotBlank

/**
 * Interface to establish a connection with InfluxDb
 */
interface InfluxDbStepConnection {
    /**
     * Configures the servers settings.
     */
    fun server(url: String, bucket: String, org: String)

    /**
     * Configures the basic authentication.
     */
    fun basic(user: String, password: String)

    /**
     * When it is called, it sets the variable gzipEnabled of the specification to true, which will call influxDb.enableGzip() when creating the connection.
     */
    fun enableGzip()
}


class InfluxDbStepConnectionImpl : InfluxDbStepConnection {

    @field:NotBlank
    var url = "http://127.0.0.1:8086"

    @field:NotBlank
    var bucket = ""

    var user: String = ""

    var password: String = ""

    var org: String = ""

    var gzipEnabled = false

    override fun server(url: String, bucket: String, org: String) {
        this.url = url
        this.bucket = bucket
        this.org = org
    }

    override fun basic(user: String, password: String) {
        this.user = user
        this.password = password
    }

    override fun enableGzip() {
        this.gzipEnabled = true
    }
}
