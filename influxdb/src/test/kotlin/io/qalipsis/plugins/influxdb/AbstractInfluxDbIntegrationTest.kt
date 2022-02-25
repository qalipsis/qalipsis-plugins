package io.qalipsis.plugins.influxdb

import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.influxdb.poll.InfluxDbPollStepConnectionImpl
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.containers.InfluxDBContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import kotlin.math.pow


@WithMockk
@Testcontainers
internal abstract class AbstractInfluxDbIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    lateinit var client: InfluxDBClientKotlin

    protected val connectionConfig = InfluxDbPollStepConnectionImpl()

    @BeforeEach
    fun beforeAll() {
        connectionConfig.url = influxDBContainer.url
        connectionConfig.password = "passpasspass"
        connectionConfig.user = "user"
        connectionConfig.org = ORGANIZATION
        connectionConfig.bucket = BUCKET

        val influxDBClient = InfluxDBClientFactory.create(
            connectionConfig.url,
            connectionConfig.user,
            connectionConfig.password.toCharArray()
        )

        val authorizationsApi = influxDBClient
            .authorizationsApi
            .findAuthorizationsByUserName("user")
        var token: CharArray = charArrayOf()
        for (authorization in authorizationsApi) {
            log.debug("token = " + authorization.token)
            token = authorization.token.toCharArray()
        }

        client = InfluxDBClientKotlinFactory.create(
            connectionConfig.url,
            token, connectionConfig.org, connectionConfig.bucket
        )
    }

    @AfterEach
    fun afterAll() {
        client.close()
    }

    companion object {

        const val BUCKET = "thebucket"

        const val ORGANIZATION = "theorganization"

        @Container
        @JvmStatic
        val influxDBContainer = InfluxDBContainer<Nothing>(DockerImageName.parse("influxdb:2.1"))
            .apply {
                waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)))
                withCreateContainerCmdModifier { cmd ->
                    cmd.hostConfig!!.withMemory(512 * 1024.0.pow(2).toLong()).withCpuCount(2)
                }
                withEnv("DOCKER_INFLUXDB_INIT_MODE", "setup")
                withEnv("DOCKER_INFLUXDB_INIT_USERNAME", "user")
                withEnv("DOCKER_INFLUXDB_INIT_PASSWORD", "passpasspass")
                withEnv("DOCKER_INFLUXDB_INIT_ORG", ORGANIZATION)
                withEnv("DOCKER_INFLUXDB_INIT_BUCKET", BUCKET)
                withEnv("DOCKER_INFLUXDB_INIT_RETENTION", "200d")
            }

        @JvmStatic
        val log = logger()
    }
}
