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

package io.qalipsis.plugins.graphite.config

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.isEmpty
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.MeasurementPublisherFactory
import io.qalipsis.plugins.graphite.Constants
import io.qalipsis.plugins.graphite.monitoring.meters.GraphiteMeasurementPublisher
import jakarta.inject.Inject
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration

@Testcontainers
internal class GraphiteMeasurementRegistryFactoryIntegrationTest {

    @Nested
    @MicronautTest(startApplication = false, environments = ["graphite"])
    inner class NoMicronautGraphiteMeterRegistry {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        @Test
        @Timeout(10)
        internal fun `should disable the default graphite meter registry`() {
            assertThat(applicationContext.getBeansOfType(MeasurementPublisherFactory::class.java)).isEmpty()
        }
    }

    @Nested
    @MicronautTest(startApplication = false, environments = ["graphite"])
    inner class WithoutMeters : TestPropertyProvider {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        override fun getProperties(): MutableMap<String, String> {
            return mutableMapOf(
                "meters.export.enabled" to "false",
                "meters.export.graphite.enabled" to "true"
            )
        }

        @Test
        @Timeout(10)
        internal fun `should start without graphite meter registry`() {
            assertThat(applicationContext.getBeansOfType(MeasurementPublisherFactory::class.java)).isEmpty()
        }
    }

    @Nested
    @MicronautTest(startApplication = false, environments = ["graphite"])
    inner class WithMetersButWithoutGraphite : TestPropertyProvider {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        override fun getProperties(): MutableMap<String, String> {
            return mutableMapOf(
                "meters.export.enabled" to "true",
                "meters.export.graphite.enabled" to "false"
            )
        }

        @Test
        @Timeout(10)
        internal fun `should start without graphite meter registry`() {
            assertThat(applicationContext.getBeansOfType(CampaignMeterRegistry::class.java)).isNotEmpty()
            assertThat(applicationContext.getBeansOfType(MeasurementPublisherFactory::class.java)).isEmpty()
        }
    }

    @Nested
    @MicronautTest(startApplication = false, environments = ["graphite"])
    inner class WithGraphiteMeterRegistry : TestPropertyProvider {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        override fun getProperties(): MutableMap<String, String> {
            return mutableMapOf(
                "meters.export.enabled" to "true",
                "meters.export.graphite.enabled" to "true",
                "meters.export.graphite.host" to CONTAINER.host,
                "meters.export.graphite.port" to CONTAINER.getMappedPort(Constants.HTTP_PORT).toString()
            )
        }

        @Test
        @Timeout(10)
        internal fun `should start with graphite meter registry`() {
            assertThat(applicationContext.getBeansOfType(MeasurementPublisherFactory::class.java)).any {
                it.isInstanceOf(GraphiteMeasurementRegistryFactory::class).all {
                    prop(GraphiteMeasurementRegistryFactory::getPublisher).isNotNull()
                        .isInstanceOf(GraphiteMeasurementPublisher::class.java)
                }
            }
        }
    }

    companion object {

        @Container
        @JvmStatic
        private val CONTAINER = GenericContainer<Nothing>(
            DockerImageName.parse(Constants.GRAPHITE_IMAGE_NAME)
        ).apply {
            setWaitStrategy(HostPortWaitStrategy())
            withExposedPorts(Constants.HTTP_PORT, Constants.GRAPHITE_PLAINTEXT_PORT, Constants.GRAPHITE_PICKLE_PORT)
            withAccessToHost(true)
            withStartupTimeout(Duration.ofSeconds(60))
            withCreateContainerCmdModifier { it.hostConfig!!.withMemory((512 * 1e20).toLong()).withCpuCount(2) }
        }
    }
}