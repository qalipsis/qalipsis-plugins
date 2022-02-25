package io.qalipsis.plugins.influxdb.save

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import com.influxdb.client.write.Point
import io.aerisconsulting.catadioptre.getProperty
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.DummyStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.influxdb.influxdb
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test

internal class InfluxDbSaveStepSpecificationImplTest {

    private val bucketName: (suspend (ctx: StepContext<*, *>, input: Any?) -> String) = { _, _ -> "test" }

    private val orgName: (suspend (ctx: StepContext<*, *>, input: Any) -> String) = { _, _ -> "testtesttest" }

    private val pointSupplier: (suspend (ctx: StepContext<*, *>, input: Any?) -> List<Point>) = { _, _ ->
        listOf(
            Point.measurement("temp").addTag("tag1", "first").addField("key1", "val1"),
            Point.measurement("temp").addTag("tag2", "second").addField("key2", "val2"),
            Point.measurement("temp").addTag("tag3", "third").addField("key3", "val3")
        )
    }

    @Test
    fun `should add minimal configuration for the step`() = runBlockingTest {
        val previousStep = DummyStepSpecification()
        previousStep.influxdb().save {
            name = "my-save-step"
            connect {
                server(
                    url = "http://localhost:8080",
                    bucket = "test",
                    org = "testtesttest"
                )
                basic(
                    user = "user",
                    password = "passpasspass"
                )
            }
            query {
                bucket = bucketName
                organization = orgName
                points = pointSupplier
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(InfluxDbSaveStepSpecificationImpl::class).all {
            prop("name") { InfluxDbSaveStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-save-step")
            prop(InfluxDbSaveStepSpecificationImpl<*>::clientBuilder).isNotNull()
            prop(InfluxDbSaveStepSpecificationImpl<*>::queryConfiguration).all {
                prop(InfluxDbSavePointConfiguration<*>::points).isEqualTo(pointSupplier)
            }
            prop(InfluxDbSaveStepSpecificationImpl<*>::monitoringConfig).isNotNull().all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isFalse()
            }
        }

        val step: InfluxDbSaveStepSpecificationImpl<*> =
            previousStep.nextSteps[0] as InfluxDbSaveStepSpecificationImpl<*>

        val bucket =
            step.queryConfiguration.getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> String>("bucket")
        assertThat(bucket(relaxedMockk(), relaxedMockk())).isEqualTo("test")

        val org =
            step.queryConfiguration.getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> String>("organization")
        assertThat(org(relaxedMockk(), relaxedMockk())).isEqualTo("testtesttest")
    }

    @Test
    fun `should add a complete configuration for the step`() = runBlockingTest {
        val previousStep = DummyStepSpecification()
        previousStep.influxdb().save {
            name = "my-save-step"
            connect {
                server(
                    url = "http://localhost:8080",
                    bucket = "test",
                    org = "testtesttest"
                )
                basic(
                    user = "user",
                    password = "passpasspass"
                )
            }
            query {
                bucket = bucketName
                organization = orgName
                points = pointSupplier
            }
            monitoring {
                events = true
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(InfluxDbSaveStepSpecificationImpl::class).all {
            prop("name") { InfluxDbSaveStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-save-step")
            prop(InfluxDbSaveStepSpecificationImpl<*>::clientBuilder).isNotNull()
            prop(InfluxDbSaveStepSpecificationImpl<*>::queryConfiguration).all {
                prop(InfluxDbSavePointConfiguration<*>::points).isEqualTo(pointSupplier)
            }
            prop(InfluxDbSaveStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isFalse()
            }
        }

        val step: InfluxDbSaveStepSpecificationImpl<*> =
            previousStep.nextSteps[0] as InfluxDbSaveStepSpecificationImpl<*>

        val bucket =
            step.queryConfiguration.getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> String>("bucket")
        assertThat(bucket(relaxedMockk(), relaxedMockk())).isEqualTo("test")

        val org =
            step.queryConfiguration.getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> String>("organization")
        assertThat(org(relaxedMockk(), relaxedMockk())).isEqualTo("testtesttest")
    }
}
