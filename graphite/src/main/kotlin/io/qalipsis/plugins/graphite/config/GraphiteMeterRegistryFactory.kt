package io.qalipsis.plugins.graphite.config

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.util.HierarchicalNameMapper
import io.micrometer.graphite.GraphiteConfig
import io.micrometer.graphite.GraphiteMeterRegistry
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.core.naming.conventions.StringConvention
import io.micronaut.core.util.StringUtils
import io.qalipsis.api.meters.MetersConfig
import jakarta.inject.Singleton
import java.util.Properties

/**
 * Configuration for the export of micrometer [io.micrometer.core.instrument.Meter] to Graphite.
 *
 * @author Palina Bril
 */
@Factory
@Requirements(
    Requires(property = MetersConfig.ENABLED, notEquals = StringUtils.FALSE),
    Requires(property = GraphiteMeterRegistryFactory.GRAPHITE_ENABLED, notEquals = StringUtils.FALSE)
)
internal class GraphiteMeterRegistryFactory {

    @Singleton
    fun graphiteRegistry(environment: Environment): GraphiteMeterRegistry {
        val properties = Properties()
        properties.putAll(environment.getProperties(MetersConfig.CONFIGURATION, StringConvention.RAW))
        properties.putAll(environment.getProperties(MetersConfig.CONFIGURATION, StringConvention.CAMEL_CASE))

        return GraphiteMeterRegistry(object : GraphiteConfig {
            override fun get(key: String?): String? {
                return properties.getProperty(key)
            }

            override fun prefix() = "graphite"
        }, Clock.SYSTEM, HierarchicalNameMapper.DEFAULT)
    }

    companion object {

        internal const val GRAPHITE_CONFIGURATION = "${MetersConfig.CONFIGURATION}.graphite"

        internal const val GRAPHITE_ENABLED = "$GRAPHITE_CONFIGURATION.enabled"
    }
}
