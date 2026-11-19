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

package io.qalipsis.plugins.timescaledb.meter

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.micronaut.core.bind.annotation.Bindable
import io.micronaut.core.util.StringUtils
import io.qalipsis.plugins.timescaledb.dataprovider.DataProviderConfiguration
import io.r2dbc.postgresql.client.SSLMode
import java.time.Duration
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Positive

/**
 * Configuration for [TimescaledbMeterDataProvider].
 *
 * @author Eric Jessé
 */
@Requires(property = "meters.provider.timescaledb.enabled", value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
@ConfigurationProperties("meters.provider.timescaledb")
interface TimescaledbMeterDataProviderConfiguration : DataProviderConfiguration {

    @get:Bindable(defaultValue = "localhost")
    @get:NotBlank
    override val host: String

    @get:Bindable(defaultValue = "5432")
    @get:Positive
    override val port: Int

    @get:Bindable(defaultValue = "qalipsis")
    @get:NotBlank
    override val database: String

    @get:Bindable(defaultValue = "meters")
    @get:NotBlank
    override val schema: String

    @get:NotBlank
    override val username: String

    @get:NotBlank
    override val password: String

    @get:Bindable(defaultValue = "false")
    override val enableSsl: Boolean

    @get:Bindable(defaultValue = "PREFER")
    override val sslMode: SSLMode

    override val sslRootCert: String?

    override val sslCert: String?

    override val sslKey: String?

    @get:Bindable(defaultValue = "1")
    @get:Min(1)
    override val minSize: Int

    @get:Bindable(defaultValue = "4")
    @get:Min(1)
    override val maxSize: Int

    @get:Bindable(defaultValue = "PT1M")
    override val maxIdleTime: Duration

    @get:Bindable(defaultValue = "PT10M")
    override val maxLifeTime: Duration

    @get:Bindable(defaultValue = "PT30S")
    override val backgroundEvictionInterval: Duration

    @get:Bindable(defaultValue = "PT5S")
    override val maxAcquireTime: Duration

    @get:Bindable(defaultValue = "PT5S")
    override val maxCreateConnectionTime: Duration

    @get:Bindable(defaultValue = "3")
    override val acquireRetry: Int

    @get:Bindable(defaultValue = "SELECT 1")
    override val validationQuery: String

    @get:Bindable(defaultValue = "PT2S")
    override val maxValidationTime: Duration

    @get:Bindable(defaultValue = "true")
    override val initSchema: Boolean
}
