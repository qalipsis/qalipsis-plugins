package io.qalipsis.plugins.redis.lettuce.save

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionType
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.coroutines.CoroutineContext

@WithMockk
@Suppress("UNCHECKED_CAST")
internal class LettuceSaveStepSpecificationConverterTest: AbstractStepSpecificationConverterTest<LettuceSaveStepSpecificationConverter>(){

    @RelaxedMockK
    private lateinit var ioCoroutineContext: CoroutineContext

    @Test
    override fun `should not support unexpected spec`() {
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    override fun `should support expected spec`() {
        Assertions.assertTrue(converter.support(relaxedMockk<LettuceSaveStepSpecificationImpl<*>>()))
    }


    @Test
    internal fun `should convert spec with name and retry policy`() = runBlockingTest {
        // given
        val spec = LettuceSaveStepSpecificationImpl<Any>()
        spec.apply {
            name = "my-step"
            retryPolicy = mockedRetryPolicy
            connection {
                nodes = listOf("localhost:6379")
                database = 1
                redisConnectionType = RedisConnectionType.CLUSTER
            }
            records { _, _ ->
                listOf()
            }
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        val spiedConverter = spyk(converter)

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<LettuceSaveStepSpecificationImpl<*>>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(LettuceSaveStep::class).all {
                prop("id").isEqualTo("my-step")
                prop("meterRegistry").isNull()
                prop("ioCoroutineContext").isSameAs(ioCoroutineContext)
                prop("eventsLogger").isNull()
                prop("connectionFactory").isNotNull()
                prop("retryPolicy").isNotNull()
                prop("recordsFactory").isNotNull()
            }
        }
    }


    @Test
    internal fun `should convert spec without name and retry policy`() = runBlockingTest {
        // given
        val spec = LettuceSaveStepSpecificationImpl<Any>()
        spec.apply {
            connection {
                nodes = listOf("localhost:6379")
                database = 1
                redisConnectionType = RedisConnectionType.CLUSTER
            }
            records { _, _ ->
                listOf()
            }
            monitoring {
                events = true
                meters = true
            }
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter)

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<LettuceSaveStepSpecificationImpl<*>>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(LettuceSaveStep::class).all {
                prop("id").isNotNull()
                prop("connectionFactory").isNotNull()
                prop("ioCoroutineContext").isSameAs(ioCoroutineContext)
                prop("meterRegistry").isNotNull().isEqualTo(meterRegistry)
                prop("eventsLogger").isNotNull().isEqualTo(eventsLogger)
                prop("retryPolicy").isNull()
                prop("recordsFactory").isNotNull()
            }
        }
    }
}