package io.qalipsis.plugins.influxdb.config

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isNotEmpty
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.influx.InfluxMeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.qalipsis.plugins.influxdb.AbstractInfluxDbIntegrationTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.testcontainers.containers.InfluxDBContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import kotlin.math.pow

@Testcontainers
internal class InfluxDbMeterRegistryFactoryIntegrationTest {

    @Nested
    @MicronautTest(startApplication = false, propertySources = ["classpath:application-influxdb.yml"])
    inner class NoMicronautInfluxMeterRegistry : TestPropertyProvider {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        override fun getProperties(): MutableMap<String, String> {
            return mutableMapOf(
                "micronaut.metrics.export.influx.enabled" to "false"
            )
        }

        @Test
        @Timeout(10)
        internal fun `should disables the default influxdb meter registry`() {
            assertThat(applicationContext.getBeansOfType(MeterRegistry::class.java)).isNotEmpty()
            assertThat(applicationContext.getBeansOfType(InfluxMeterRegistry::class.java)).isEmpty()
        }
    }

    @Nested
    @MicronautTest(startApplication = false)
    inner class WithoutMeters : TestPropertyProvider {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        override fun getProperties(): MutableMap<String, String> {
            return mutableMapOf(
                "micronaut.metrics.export.influx.enabled" to "false",
                "meters.enabled" to "false",
                "meters.influxdb.enabled" to "true",
                "meters.influxdb.hosts" to CONTAINER.url
            )
        }

        @Test
        @Timeout(10)
        internal fun `should start without influxdb meter registry`() {
            assertThat(applicationContext.getBeansOfType(MeterRegistry::class.java)).isNotEmpty()
            assertThat(applicationContext.getBeansOfType(InfluxMeterRegistry::class.java)).isEmpty()
        }
    }

    @Nested
    @MicronautTest(startApplication = false)
    inner class WithMetersButWithoutInfluxdb : TestPropertyProvider {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        override fun getProperties(): MutableMap<String, String> {
            return mutableMapOf(
                "micronaut.metrics.export.influx.enabled" to "false",
                "meters.enabled" to "true",
                "meters.influxdb.enabled" to "false",
                "meters.influxdb.hosts" to CONTAINER.url
            )
        }

        @Test
        @Timeout(10)
        internal fun `should start without influxdb meter registry`() {
            assertThat(applicationContext.getBeansOfType(MeterRegistry::class.java)).isNotEmpty()
            assertThat(applicationContext.getBeansOfType(InfluxMeterRegistry::class.java)).isEmpty()
        }
    }

    @Nested
    @MicronautTest(startApplication = false)
    inner class WithInfluxdbMeterRegistry : TestPropertyProvider {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        override fun getProperties(): MutableMap<String, String> {
            return mutableMapOf(
                "micronaut.metrics.export.influx.enabled" to "false",
                "meters.enabled" to "true",
                "meters.influxdb.enabled" to "true",
                "meters.influxdb.hosts" to CONTAINER.url
            )
        }

        @Test
        @Timeout(10)
        internal fun `should start with influxdb meter registry`() {
            assertThat(applicationContext.getBeansOfType(MeterRegistry::class.java)).isNotEmpty()
            assertThat(applicationContext.getBeansOfType(InfluxMeterRegistry::class.java)).hasSize(1)
        }
    }

    companion object {

        @Container
        @JvmStatic
        private val CONTAINER = InfluxDBContainer<Nothing>(DockerImageName.parse("influxdb:2.1"))
            .apply {
                waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)))
                withCreateContainerCmdModifier { cmd ->
                    cmd.hostConfig!!.withMemory(512 * 1024.0.pow(2).toLong()).withCpuCount(2)
                }
                withEnv("DOCKER_INFLUXDB_INIT_MODE", "setup")
                withEnv("DOCKER_INFLUXDB_INIT_USERNAME", "user")
                withEnv("DOCKER_INFLUXDB_INIT_PASSWORD", "passpasspass")
                withEnv("DOCKER_INFLUXDB_INIT_ORG", AbstractInfluxDbIntegrationTest.ORGANIZATION)
                withEnv("DOCKER_INFLUXDB_INIT_BUCKET", AbstractInfluxDbIntegrationTest.BUCKET)
                withEnv("DOCKER_INFLUXDB_INIT_RETENTION", "200d")
            }
    }
}