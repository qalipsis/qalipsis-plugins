@file:Suppress("UNCHECKED_CAST")

package io.qalipsis.plugins.influxdb.search

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
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

/**
 *
 * @author Palina Bril
 */
@WithMockk
internal class InfluxDbSearchStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<InfluxDbSearchStepSpecificationConverter>() {

    private val queryFactory: (suspend (ctx: StepContext<*, *>, input: Any) -> String) = { _, _ -> "query" }

    @Test
    override fun `should not support unexpected spec`() {
        assertThat(converter.support(relaxedMockk()))
            .isFalse()
    }

    @Test
    override fun `should support expected spec`() {
        assertThat(converter.support(relaxedMockk<InfluxDbSearchStepSpecificationImpl<*>>()))
            .isTrue()
    }

    @Test
    fun `should convert with name and retry policy`() = runBlockingTest {
        // given
        val spec = InfluxDbSearchStepSpecificationImpl<Any>()
        spec.also {
            it.name = "influxdb-search-step"
            it.searchConfig = InfluxDbQueryConfiguration(
                query = queryFactory,
            )
            it.connect {
                server(
                    url = "http://localhost:8080",
                    bucket = "test",
                    org = "testtesttest"
                )
                basic(
                    password = "passpasspass",
                    user = "user"
                )
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
            creationContext as StepCreationContext<InfluxDbSearchStepSpecificationImpl<*>>
        )

        // then
        creationContext.createdStep!!.let { it ->
            assertThat(it).isInstanceOf(InfluxDbSearchStep::class).all {
                prop("id").isEqualTo("influxdb-search-step")
                prop("influxDbQueryClient").all {
                    prop("eventsLogger").isNull()
                    prop("meterRegistry").isSameAs(meterRegistry)
                }
                prop("retryPolicy").isNotNull()
                prop("queryFactory").isEqualTo(queryFactory)
            }
        }
    }

    @Test
    fun `should convert without name and retry policy`() = runBlockingTest {
        // given
        val spec = InfluxDbSearchStepSpecificationImpl<Any>()
        spec.also {
            it.searchConfig = InfluxDbQueryConfiguration(
                query = queryFactory,
            )
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
            it.monitoring {
                events = true
            }
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter, recordPrivateCalls = true)

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<InfluxDbSearchStepSpecificationImpl<*>>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(InfluxDbSearchStep::class).all {
                prop("id").isNotNull()
                prop("retryPolicy").isNull()
                prop("queryFactory").isEqualTo(queryFactory)
                prop("influxDbQueryClient").all {
                    prop("meterRegistry").isNull()
                    prop("eventsLogger").isSameAs(eventsLogger)
                }
            }
        }
    }
}
