/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
