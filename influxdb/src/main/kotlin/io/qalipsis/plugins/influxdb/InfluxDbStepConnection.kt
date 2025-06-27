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
