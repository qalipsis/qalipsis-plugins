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

package io.qalipsis.plugins.sql

import io.qalipsis.api.annotations.Spec
import java.time.Duration
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Positive

/**
 * Connection configuration for SQL operations using R2DBC connection pool.
 *
 * @property host database host, defaults to "localhost".
 * @property port database port.
 * @property database database name, defaults to no database.
 * @property username database username.
 * @property password password, defaults to no password.
 * @property maxSize the maximum size of the connection pool.
 * @property maxIdleTime duration for which the objects are going to be kept as idle.
 * @property maxCreateConnectionTime the timeout for connecting to servers.
 * @property applicationName optional name to be passed to the database for reporting.
 * @property options additional connection options.
 *
 * @author Eric Jessé
 */
@Spec
data class SqlConnection internal constructor(
    @field:NotBlank var host: String = "localhost",
    @field:Positive var port: Int = -1,
    @field:NotBlank var database: String? = null,
    @field:NotBlank var username: String? = null,
    var password: String? = null,
    @field:Positive var maxSize: Int = 1,
    @field:Positive var maxIdleTime: Duration = Duration.ofMinutes(1),
    @field:Positive var maxCreateConnectionTime: Duration = Duration.ofMillis(5000),
    var applicationName: String? = "sql",
    var options: Map<String, String> = emptyMap()
)
