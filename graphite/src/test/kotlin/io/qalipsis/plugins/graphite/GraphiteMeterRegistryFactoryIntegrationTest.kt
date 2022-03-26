package io.qalipsis.plugins.graphite

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isNotEmpty
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.graphite.GraphiteMeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
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
internal class GraphiteMeterRegistryFactoryIntegrationTest {

    @Nested
    @MicronautTest(startApplication = false, propertySources = ["classpath:application.yml"])
    inner class NoMicronautGraphiteMeterRegistry : TestPropertyProvider {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        override fun getProperties(): MutableMap<String, String> {
            return mutableMapOf(
                "micronaut.metrics.enabled" to "false"
            )
        }

        @Test
        @Timeout(10)
        internal fun `should disables the default graphite meter registry`() {
            assertThat(applicationContext.getBeansOfType(MeterRegistry::class.java)).isEmpty()
            assertThat(applicationContext.getBeansOfType(GraphiteMeterRegistry::class.java)).isEmpty()
        }
    }

    @Nested
    @MicronautTest(startApplication = false)
    inner class WithoutMeters : TestPropertyProvider {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        override fun getProperties(): MutableMap<String, String> {
            return mutableMapOf(
                "micronaut.metrics.enabled" to "false",
                "meters.enabled" to "false",
                "meters.graphitedb.enabled" to "true",
                "meters.graphitedb.host" to CONTAINER.host,
                "meters.graphitedb.port" to CONTAINER.getMappedPort(HTTP_PORT).toString()
            )
        }

        @Test
        @Timeout(10)
        internal fun `should start without graphite meter registry`() {
            assertThat(applicationContext.getBeansOfType(MeterRegistry::class.java)).isEmpty()
            assertThat(applicationContext.getBeansOfType(GraphiteMeterRegistry::class.java)).isEmpty()
        }
    }

    @Nested
    @MicronautTest(startApplication = false)
    inner class WithMetersButWithoutGraphite : TestPropertyProvider {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        override fun getProperties(): MutableMap<String, String> {
            return mutableMapOf(
                "micronaut.metrics.export.graphite.enabled" to "false",
                "meters.enabled" to "true",
                "meters.graphitedb.enabled" to "false",
                "meters.graphitedb.host" to CONTAINER.host,
                "meters.graphitedb.port" to CONTAINER.getMappedPort(HTTP_PORT).toString()
            )
        }

        @Test
        @Timeout(10)
        internal fun `should start without graphite meter registry`() {
            assertThat(applicationContext.getBeansOfType(MeterRegistry::class.java)).isNotEmpty()
            assertThat(applicationContext.getBeansOfType(GraphiteMeterRegistry::class.java)).isEmpty()
        }
    }

    @Nested
    @MicronautTest(startApplication = false)
    inner class WithGraphiteMeterRegistry : TestPropertyProvider {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        override fun getProperties(): MutableMap<String, String> {
            return mutableMapOf(
                "micronaut.metrics.enabled" to "false",
                "meters.enabled" to "true",
                "meters.graphitedb.enabled" to "true",
                "meters.graphitedb.host" to CONTAINER.host,
                "meters.graphitedb.port" to CONTAINER.getMappedPort(HTTP_PORT).toString()
            )
        }

        @Test
        @Timeout(10)
        internal fun `should start with graphite meter registry`() {
            assertThat(applicationContext.getBeansOfType(MeterRegistry::class.java)).isNotEmpty()
            assertThat(applicationContext.getBeansOfType(GraphiteMeterRegistry::class.java)).hasSize(1)
        }
    }

    companion object {

        const val GRAPHITE_IMAGE_NAME = "graphiteapp/graphite-statsd:latest"
        const val HTTP_PORT = 80
        const val GRAPHITE_PLAINTEXT_PORT = 2003
        const val GRAPHITE_PICKLE_PORT = 2004

        @Container
        @JvmStatic
        private val CONTAINER = GenericContainer<Nothing>(
            DockerImageName.parse(GRAPHITE_IMAGE_NAME)
        ).apply {
            setWaitStrategy(HostPortWaitStrategy())
            withExposedPorts(HTTP_PORT, GRAPHITE_PLAINTEXT_PORT, GRAPHITE_PICKLE_PORT)
            withAccessToHost(true)
            withStartupTimeout(Duration.ofSeconds(60))
            withCreateContainerCmdModifier { it.hostConfig!!.withMemory((512 * 1e20).toLong()).withCpuCount(2) }
        }
    }
}