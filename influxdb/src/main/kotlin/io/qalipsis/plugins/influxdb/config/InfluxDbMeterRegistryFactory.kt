package io.qalipsis.plugins.influxdb.config

import io.micrometer.core.instrument.Clock
import io.micrometer.influx.InfluxConfig
import io.micrometer.influx.InfluxMeterRegistry
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.core.naming.conventions.StringConvention
import io.micronaut.core.util.StringUtils
import io.qalipsis.api.config.MetersConfig
import jakarta.inject.Singleton
import java.util.Properties

/**
 * Configuration for the export of micrometer [io.micrometer.core.instrument.Meter] to InfluxDb.
 *
 * @author Palina Bril
 */
@Factory
@Requirements(
    Requires(property = MetersConfig.EXPORT_ENABLED, notEquals = StringUtils.FALSE),
    Requires(property = InfluxDbMeterRegistryFactory.INBLUXDB_ENABLED, notEquals = StringUtils.FALSE)
)
internal class InfluxDbMeterRegistryFactory {

    @Singleton
    fun influxdbRegistry(environment: Environment): InfluxMeterRegistry {
        val properties = Properties()
        properties.putAll(environment.getProperties(MetersConfig.EXPORT_CONFIGURATION, StringConvention.RAW))
        properties.putAll(environment.getProperties(MetersConfig.EXPORT_CONFIGURATION, StringConvention.CAMEL_CASE))

        return InfluxMeterRegistry(object : InfluxConfig {
            override fun get(key: String): String? {
                return properties.getProperty(key)
            }

            override fun prefix() = "influxdb"
            override fun org(): String = "qalipsis"
            override fun bucket(): String = "qalipsis-event"
            override fun userName(): String = ""
            override fun password(): String = ""
            override fun token(): String = "any"
        }, Clock.SYSTEM)
    }

    companion object {

        private const val INFLUXDB_CONFIGURATION = "${MetersConfig.EXPORT_CONFIGURATION}.influxdb"

        const val INBLUXDB_ENABLED = "$INFLUXDB_CONFIGURATION.enabled"
    }
}
