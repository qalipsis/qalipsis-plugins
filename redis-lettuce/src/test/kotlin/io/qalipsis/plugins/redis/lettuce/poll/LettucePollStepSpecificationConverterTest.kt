package io.qalipsis.plugins.redis.lettuce.poll

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import io.aerisconsulting.catadioptre.getProperty
import io.aerisconsulting.catadioptre.invokeInvisible
import io.micrometer.core.instrument.Counter
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.api.steps.datasource.IterativeDatasourceStep
import io.qalipsis.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.qalipsis.plugins.redis.lettuce.Monitoring
import io.qalipsis.plugins.redis.lettuce.poll.converters.PollResultSetBatchConverter
import io.qalipsis.plugins.redis.lettuce.poll.converters.PollResultSetSingleConverter
import io.qalipsis.plugins.redis.lettuce.poll.converters.RedisToJavaConverter
import io.qalipsis.plugins.redis.lettuce.poll.scanners.LettuceKeysScanner
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyNever
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 *
 * @author Gabriel Moraes
 */
@WithMockk
internal class LettucePollStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<LettucePollStepSpecificationConverter>() {

    @RelaxedMockK
    private lateinit var lettucePollMetrics: Monitoring

    @RelaxedMockK
    private lateinit var redisToJavaConverter: RedisToJavaConverter

    @RelaxedMockK
    private lateinit var ioCoroutineScope: CoroutineScope

    @Test
    override fun `should not support unexpected spec`() {
        assertThat(converter.support(relaxedMockk()))
            .isFalse()
    }

    @Test
    override fun `should support expected spec`() {
        assertThat(converter.support(relaxedMockk<LettucePollStepSpecificationImpl<*>>()))
            .isTrue()

    }

    @ExperimentalCoroutinesApi
    @Test
    @Suppress("UNCHECK_CAST")
    fun `should convert with name`() {
        // given
        val spec = LettucePollStepSpecificationImpl<Any>(RedisLettuceScanMethod.SCAN)
        spec.also {
            it.name = "redis-lettuce-poll-step"
            it.keyOrPattern = "test"
            it.connection {
                nodes = listOf("localhost:6379")
            }
            it.monitoringConfiguration = lettucePollMetrics
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter, recordPrivateCalls = true)

        val recordsConverter: DatasourceObjectConverter<PollRawResult<*>, out Any> = relaxedMockk()
        every { spiedConverter["buildConverter"](any<String>(), refEq(spec)) } returns recordsConverter


        // when
        runBlocking {
            @Suppress("UNCHECKED_CAST")
            spiedConverter.convert<Unit, Map<String, *>>(
                creationContext as StepCreationContext<LettucePollStepSpecificationImpl<*>>
            )
        }

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(IterativeDatasourceStep::class).all {
                prop("id").isNotNull().isEqualTo("redis-lettuce-poll-step")
                prop("reader").isNotNull().isInstanceOf(LettuceIterativeReader::class).all {
                    prop("ioCoroutineScope").isSameAs(ioCoroutineScope)
                    prop("connectionFactory").isNotNull()
                    prop("pattern").isEqualTo("test")
                    prop("pollDelay").isEqualTo(Duration.ofSeconds(10))
                    prop("lettuceScanner").isNotNull().isInstanceOf(LettuceKeysScanner::class)
                    prop("resultsChannelFactory").isNotNull()
                }
                prop("processor").isNotNull().isInstanceOf(NoopDatasourceObjectProcessor::class)
                prop("converter").isNotNull().isSameAs(recordsConverter)
            }
        }
        verifyOnce { spiedConverter["buildConverter"](eq(creationContext.createdStep!!.id), refEq(spec)) }
        verifyNever { spiedConverter["buildConverter"](neq(creationContext.createdStep!!.id), any<LettucePollStepSpecificationImpl<*>>()) }

        val channelFactory = creationContext.createdStep!!
            .getProperty<LettuceIterativeReader>("reader")
            .getProperty<() -> Channel<Any>>("resultsChannelFactory")
        val createdChannel = channelFactory()
        assertThat(createdChannel).all {
            transform { it.isEmpty }.isTrue()
            transform { it.isClosedForReceive }.isFalse()
            transform { it.isClosedForSend }.isFalse()
        }
    }

    @Test
    @Suppress("UNCHECK_CAST")
    fun `should convert without name`() {
        // given
        val spec = LettucePollStepSpecificationImpl<Any>(RedisLettuceScanMethod.SCAN)
        spec.also {
            it.keyOrPattern = "test"
            it.connection {
                nodes = listOf("localhost:6379")
            }
            it.monitoringConfiguration = lettucePollMetrics
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter, recordPrivateCalls = true)

        val recordsConverter: DatasourceObjectConverter<PollRawResult<*>, out Any> = relaxedMockk()
        val stepIdSlot = slot<String>()
        every { spiedConverter["buildConverter"](capture(stepIdSlot), refEq(spec)) } returns recordsConverter

        // when
        runBlocking {
            @Suppress("UNCHECKED_CAST")
            spiedConverter.convert<Unit, Map<String, *>>(
                creationContext as StepCreationContext<LettucePollStepSpecificationImpl<*>>
            )
        }

        // then
        creationContext.createdStep!!.let { it ->
            assertThat(it).isInstanceOf(IterativeDatasourceStep::class).all {
                prop("id").isNotNull().isEqualTo(stepIdSlot.captured)
                prop("reader").isNotNull().isInstanceOf(LettuceIterativeReader::class).all {
                    prop("ioCoroutineScope").isSameAs(ioCoroutineScope)
                    prop("connectionFactory").isNotNull()
                    prop("pattern").isEqualTo("test")
                    prop("pollDelay").isEqualTo(Duration.ofSeconds(10))
                    prop("lettuceScanner").isNotNull().isInstanceOf(LettuceKeysScanner::class)
                    prop("resultsChannelFactory").isNotNull()
                }
                prop("processor").isNotNull().isInstanceOf(NoopDatasourceObjectProcessor::class)
                prop("converter").isNotNull().isSameAs(recordsConverter)
            }
        }
        verifyOnce { spiedConverter["buildConverter"](eq(creationContext.createdStep!!.id), refEq(spec)) }
    }

    @Test
    fun `should build batch converter without counter`() {
        // given
        val spec = LettucePollStepSpecificationImpl<Any>(RedisLettuceScanMethod.ZSCAN)

        // when
        val converter = converter.invokeInvisible<DatasourceObjectConverter<PollRawResult<*>, out Any>>("buildConverter","my-step", spec)

        // then
        assertThat(converter).isInstanceOf(PollResultSetBatchConverter::class).all {
            prop("recordsCounter").isNull()
            prop("recordsBytes").isNull()
        }
    }

    @Test
    fun `should build batch converter with counter`() {
        // given
        val spec = LettucePollStepSpecificationImpl<Any>(RedisLettuceScanMethod.ZSCAN)
        spec.monitoring {
            events = true
            meters = true
        }

        val counter: Counter = mockk()
        val counterBytes: Counter = mockk()
        every {
            meterRegistry.counter(eq("redis-lettuce-zscan-poll-records"), eq("step"), eq("my-step"))
        } returns counter
        every {
            meterRegistry.counter(eq("redis-lettuce-zscan-poll-records-bytes"), eq("step"), eq("my-step"))
        } returns counterBytes


        // when
        val converter = converter.invokeInvisible<DatasourceObjectConverter<PollRawResult<*>, out Any>>("buildConverter", "my-step", spec)

        // then
        assertThat(converter).isInstanceOf(PollResultSetBatchConverter::class).all {
            prop("recordsCounter").isEqualTo(counter)
            prop("recordsBytes").isEqualTo(counterBytes)
        }

        verifyOnce { meterRegistry.counter(eq("redis-lettuce-zscan-poll-records"), eq("step"), eq("my-step")) }
        verifyOnce { meterRegistry.counter(eq("redis-lettuce-zscan-poll-records-bytes"), eq("step"), eq("my-step")) }
        confirmVerified(meterRegistry)
    }

    @Test
    fun `should build single converter without metrics`() {
        // given
        val spec = LettucePollStepSpecificationImpl<Any>(RedisLettuceScanMethod.ZSCAN)

        spec.flatten()

        // when
        val converter = converter.invokeInvisible<DatasourceObjectConverter<PollRawResult<*>, out Any>>("buildConverter", "my-step", spec)

        // then
        assertThat(converter).isInstanceOf(PollResultSetSingleConverter::class).all {
            prop("recordsCounter").isNull()
            prop("recordsBytes").isNull()
        }


    }

    @Test
    fun `should build single converter with counter`() {
        // given
        val spec = LettucePollStepSpecificationImpl<Any>(RedisLettuceScanMethod.ZSCAN)
        spec.monitoring {
            events = true
            meters = true
        }

        spec.flatten()

        val counter: Counter = mockk()
        val counterBytes: Counter = mockk()
        every {
            meterRegistry.counter(
                eq("redis-lettuce-zscan-poll-records"),
                eq("step"),
                eq("my-step")
            )
        } returns counter
        every {
            meterRegistry.counter(
                eq("redis-lettuce-zscan-poll-records-bytes"),
                eq("step"),
                eq("my-step")
            )
        } returns counterBytes

        // when
        val converter = converter.invokeInvisible<DatasourceObjectConverter<PollRawResult<*>, out Any>>("buildConverter","my-step", spec)

        // then
        assertThat(converter).isInstanceOf(PollResultSetSingleConverter::class).all {
            prop("recordsCounter").isEqualTo(counter)
            prop("recordsBytes").isEqualTo(counterBytes)
        }

        verifyOnce { meterRegistry.counter(eq("redis-lettuce-zscan-poll-records"), eq("step"), eq("my-step")) }
        verifyOnce { meterRegistry.counter(eq("redis-lettuce-zscan-poll-records-bytes"), eq("step"), eq("my-step")) }
        confirmVerified(meterRegistry)
    }


}
