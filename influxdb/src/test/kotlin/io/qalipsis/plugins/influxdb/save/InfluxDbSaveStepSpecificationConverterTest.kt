@file:Suppress("UNCHECKED_CAST")

package io.qalipsis.plugins.influxdb.save

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import com.influxdb.client.write.Point
import io.mockk.spyk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test

@WithMockk
internal class InfluxDbSaveStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<InfluxDbSaveStepSpecificationConverter>() {

    private val bucketName: (suspend (ctx: StepContext<*, *>, input: Any?) -> String) = { _, _ -> "test" }

    private val orgName: (suspend (ctx: StepContext<*, *>, input: Any) -> String) = { _, _ -> "testtesttest" }

    private val pointSupplier: (suspend (ctx: StepContext<*, *>, input: Any?) -> List<Point>) = { _, _ ->
        listOf(
            Point.measurement("temp").addTag("tag1", "first").addField("key1", "val1"),
            Point.measurement("temp").addTag("tag2", "second").addField("key2", "val3"),
            Point.measurement("temp").addTag("tag3", "third").addField("key3", "val3")
        )
    }

    @Test
    override fun `should not support unexpected spec`() {
        assertThat(converter.support(relaxedMockk()))
            .isFalse()
    }

    @Test
    override fun `should support expected spec`() {
        assertThat(converter.support(relaxedMockk<InfluxDbSaveStepSpecificationImpl<*>>()))
            .isTrue()
    }

    @Test
    fun `should convert with name, retry policy and meters`() = runBlockingTest {
        // given
        val spec = InfluxDbSaveStepSpecificationImpl<Any>()
        spec.also {
            it.name = "influxdb-save-step"
            it.connect {
                server(
                    url = "http://localhost:8080",
                    org = "testtesttest",
                    bucket = "test"
                )
                basic(
                    password = "passpasspass",
                    user = "user"
                )
            }
            it.query {
                bucket = bucketName
                organization = orgName
                points = pointSupplier
            }
            it.retryPolicy = mockedRetryPolicy
            it.monitoring {
                meters = true
            }
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter, recordPrivateCalls = true)
        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<InfluxDbSaveStepSpecificationImpl<*>>
        )

        // then
        assertThat(creationContext.createdStep!!).all {
            prop("id").isEqualTo("influxdb-save-step")
            prop("influxDbSavePointClient").all {
                prop("clientBuilder").isNotNull()
                prop("meterRegistry").isSameAs(meterRegistry)
                prop("eventsLogger").isNull()
            }
            prop("retryPolicy").isNotNull()
            prop("bucketName").isEqualTo(bucketName)
            prop("orgName").isEqualTo(orgName)
            prop("pointsFactory").isSameAs(pointSupplier)
        }
    }

    @Test
    fun `should convert without name and retry policy but with events`() = runBlockingTest {
        // given
        val spec = InfluxDbSaveStepSpecificationImpl<Any>()
        spec.also {
            it.connect {
                server(
                    url = "http://localhost:8080",
                    org = "testtesttest",
                    bucket = "test"
                )
                basic(
                    password = "passpasspass",
                    user = "user"
                )
            }
            it.query {
                bucket = bucketName
                organization = orgName
                points = pointSupplier
            }
            it.monitoring {
                events = true
            }
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter, recordPrivateCalls = true)


        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<InfluxDbSaveStepSpecificationImpl<*>>
        )

        // then
        assertThat(creationContext.createdStep!!).all {
            prop("retryPolicy").isNull()
            prop("bucketName").isEqualTo(bucketName)
            prop("orgName").isEqualTo(orgName)
            prop("pointsFactory").isSameAs(pointSupplier)
            prop("influxDbSavePointClient").all {
                prop("clientBuilder").isNotNull()
                prop("meterRegistry").isNull()
                prop("eventsLogger").isSameAs(eventsLogger)
            }
        }
    }
}
