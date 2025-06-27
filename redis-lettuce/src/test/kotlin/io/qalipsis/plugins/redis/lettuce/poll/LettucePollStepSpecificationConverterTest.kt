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
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.mockk.spyk
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.api.steps.datasource.IterativeDatasourceStep
import io.qalipsis.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.qalipsis.plugins.redis.lettuce.poll.converters.PollResultSetBatchConverter
import io.qalipsis.plugins.redis.lettuce.poll.converters.PollResultSetSingleConverter
import io.qalipsis.plugins.redis.lettuce.poll.converters.RedisToJavaConverter
import io.qalipsis.plugins.redis.lettuce.poll.scanners.LettuceKeysScanner
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
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
    private lateinit var lettucePollMetrics: StepMonitoringConfiguration

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
            it.monitoringConfig = lettucePollMetrics
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter, recordPrivateCalls = true)

        val recordsConverter: DatasourceObjectConverter<PollRawResult<*>, out Any> = relaxedMockk()
        every { spiedConverter["buildConverter"](refEq(spec)) } returns recordsConverter

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
                prop("name").isNotNull().isEqualTo("redis-lettuce-poll-step")
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
        verifyOnce { spiedConverter["buildConverter"](refEq(spec)) }

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
            it.monitoringConfig = lettucePollMetrics
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter, recordPrivateCalls = true)

        val recordsConverter: DatasourceObjectConverter<PollRawResult<*>, out Any> = relaxedMockk()
        val stepIdSlot = slot<String>()
        every { spiedConverter["buildConverter"](refEq(spec)) } returns recordsConverter

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
                prop("name").isEqualTo("")
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
        verifyOnce { spiedConverter["buildConverter"](refEq(spec)) }
    }

    @Test
    fun `should build batch converter without monitor and logger`() {
        // given
        val spec = LettucePollStepSpecificationImpl<Any>(RedisLettuceScanMethod.ZSCAN)

        // when
        val converter =
            converter.invokeInvisible<DatasourceObjectConverter<PollRawResult<*>, out Any>>("buildConverter", spec)

        // then
        assertThat(converter).isInstanceOf(PollResultSetBatchConverter::class).all {
            prop("eventPrefix").isEqualTo("redis.lettuce.poll.zscan")
            prop("meterPrefix").isEqualTo("redis-lettuce-poll-zscan")
            prop("eventsLogger").isNull()
            prop("meterRegistry").isNull()
        }
    }

    @Test
    fun `should build batch converter with monitor and logger`() {
        // given
        val spec = LettucePollStepSpecificationImpl<Any>(RedisLettuceScanMethod.ZSCAN)
        spec.monitoring {
            events = true
            meters = true
        }

        // when
        val converter =
            converter.invokeInvisible<DatasourceObjectConverter<PollRawResult<*>, out Any>>("buildConverter", spec)

        // then
        assertThat(converter).isInstanceOf(PollResultSetBatchConverter::class).all {
            prop("eventPrefix").isEqualTo("redis.lettuce.poll.zscan")
            prop("meterPrefix").isEqualTo("redis-lettuce-poll-zscan")
            prop("eventsLogger").isNotNull()
            prop("meterRegistry").isNotNull()
        }
    }

    @Test
    fun `should build single converter without monitor and logger`() {
        // given
        val spec = LettucePollStepSpecificationImpl<Any>(RedisLettuceScanMethod.ZSCAN)

        spec.flatten()

        // when
        val converter =
            converter.invokeInvisible<DatasourceObjectConverter<PollRawResult<*>, out Any>>("buildConverter", spec)

        // then
        assertThat(converter).isInstanceOf(PollResultSetSingleConverter::class).all {
            prop("eventPrefix").isEqualTo("redis.lettuce.poll.zscan")
            prop("meterPrefix").isEqualTo("redis-lettuce-poll-zscan")
            prop("eventsLogger").isNull()
            prop("meterRegistry").isNull()
        }


    }

    @Test
    fun `should build single converter with monitor and logger`() {
        // given
        val spec = LettucePollStepSpecificationImpl<Any>(RedisLettuceScanMethod.ZSCAN)
        spec.monitoring {
            events = true
            meters = true
        }

        spec.flatten()
        // when
        val converter =
            converter.invokeInvisible<DatasourceObjectConverter<PollRawResult<*>, out Any>>("buildConverter", spec)

        // then
        assertThat(converter).isInstanceOf(PollResultSetSingleConverter::class).all {
            prop("eventPrefix").isEqualTo("redis.lettuce.poll.zscan")
            prop("meterPrefix").isEqualTo("redis-lettuce-poll-zscan")
            prop("eventsLogger").isNotNull()
            prop("meterRegistry").isNotNull()
        }

    }
}
