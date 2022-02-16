package io.qalipsis.plugins.r2dbc.jasync.save

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import com.github.jasync.sql.db.SSLConfiguration
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.DummyStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.r2dbc.jasync.JasyncConnection
import io.qalipsis.plugins.r2dbc.jasync.dialect.Protocol
import io.qalipsis.plugins.r2dbc.jasync.r2dbcJasync
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * @author Fiodar Hmyza
 */
internal class JasyncSaveStepSpecificationTest {

    @Test
    internal fun `should add specification to the step`() {
        val previousStep = DummyStepSpecification()
        val tableNameFactory: suspend (ctx: StepContext<*, *>, input: Int) -> String = relaxedMockk()
        val columnsFactory: suspend (ctx: StepContext<*, *>, input: Int) -> List<String> = relaxedMockk()
        val rowsFactory: suspend (ctx: StepContext<*, *>, input: Int) -> List<JasyncSaveRecord> = relaxedMockk()
        previousStep.r2dbcJasync().save {
            protocol(Protocol.POSTGRESQL)
            tableName(tableNameFactory)
            columns(columnsFactory)
            values(rowsFactory)
            connection {
                host = "my-server"
                port = 5678
                database = "my-other-database"
                username = "my-other-username"
                password = "my-other-password"
            }
            monitoring {
                events = true
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(JasyncSaveStepSpecificationImpl::class).all {
            prop(JasyncSaveStepSpecificationImpl<*>::name).isEmpty()
            prop(JasyncSaveStepSpecificationImpl<*>::protocol).isEqualTo(Protocol.POSTGRESQL)
            prop(JasyncSaveStepSpecificationImpl<*>::tableNameFactory).isSameAs(tableNameFactory)
            prop(JasyncSaveStepSpecificationImpl<*>::columnsFactory).isSameAs(columnsFactory)
            prop(JasyncSaveStepSpecificationImpl<*>::rowsFactory).isSameAs(rowsFactory)
            prop(JasyncSaveStepSpecificationImpl<*>::connection).all {
                prop(JasyncConnection::host).isEqualTo("my-server")
                prop(JasyncConnection::port).isEqualTo(5678)
                prop(JasyncConnection::database).isEqualTo("my-other-database")
                prop(JasyncConnection::username).isEqualTo("my-other-username")
                prop(JasyncConnection::password).isEqualTo("my-other-password")
                prop(JasyncConnection::queryTimeout).isNull()
                prop(JasyncConnection::ssl).all {
                    prop(SSLConfiguration::mode).isEqualTo(SSLConfiguration.Mode.Disable)
                    prop(SSLConfiguration::rootCert).isNull()
                }
                prop(JasyncConnection::charset).isEqualTo(StandardCharsets.UTF_8)
                prop(JasyncConnection::maximumMessageSize).isEqualTo(16777216)
            }
            prop(JasyncSaveStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isFalse()
            }
        }
    }

    @Test
    internal fun `should add a complete specification to the step with monitoring`() {
        val previousStep = DummyStepSpecification()
        val tableNameFactory: suspend (ctx: StepContext<*, *>, input: Int) -> String = relaxedMockk()
        val columnsFactory: suspend (ctx: StepContext<*, *>, input: Int) -> List<String> = relaxedMockk()
        val rowsFactory: suspend (ctx: StepContext<*, *>, input: Int) -> List<JasyncSaveRecord> = relaxedMockk()
        val rootCert = File("root-cert")
        previousStep.r2dbcJasync().save {
            protocol(Protocol.POSTGRESQL)
            tableName(tableNameFactory)
            columns(columnsFactory)
            values(rowsFactory)
            connection {
                host = "my-server"
                port = 5678
                database = "my-other-database"
                username = "my-other-username"
                password = "my-other-password"
                queryTimeout = Duration.ofSeconds(60)
                ssl = SSLConfiguration(SSLConfiguration.Mode.Prefer, rootCert)
                charset = StandardCharsets.ISO_8859_1
                maximumMessageSize = 151424
            }
            monitoring {
                meters = true
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(JasyncSaveStepSpecificationImpl::class).all {
            prop(JasyncSaveStepSpecificationImpl<*>::name).isEmpty()
            prop(JasyncSaveStepSpecificationImpl<*>::protocol).isEqualTo(Protocol.POSTGRESQL)
            prop(JasyncSaveStepSpecificationImpl<*>::tableNameFactory).isSameAs(tableNameFactory)
            prop(JasyncSaveStepSpecificationImpl<*>::columnsFactory).isSameAs(columnsFactory)
            prop(JasyncSaveStepSpecificationImpl<*>::rowsFactory).isSameAs(rowsFactory)
            prop(JasyncSaveStepSpecificationImpl<*>::connection).all {
                prop(JasyncConnection::host).isEqualTo("my-server")
                prop(JasyncConnection::port).isEqualTo(5678)
                prop(JasyncConnection::database).isEqualTo("my-other-database")
                prop(JasyncConnection::username).isEqualTo("my-other-username")
                prop(JasyncConnection::password).isEqualTo("my-other-password")
                prop(JasyncConnection::queryTimeout).isEqualTo(Duration.ofSeconds(60))
                prop(JasyncConnection::ssl).all {
                    prop(SSLConfiguration::mode).isEqualTo(SSLConfiguration.Mode.Prefer)
                    prop(SSLConfiguration::rootCert).isSameAs(rootCert)
                }
                prop(JasyncConnection::charset).isEqualTo(StandardCharsets.ISO_8859_1)
                prop(JasyncConnection::maximumMessageSize).isEqualTo(151424)
            }
            prop(JasyncSaveStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

}
