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

package io.qalipsis.plugins.graphite.poll

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
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
import io.mockk.mockk
import io.mockk.spyk
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.api.steps.datasource.IterativeDatasourceStep
import io.qalipsis.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.qalipsis.plugins.graphite.poll.converters.GraphitePollBatchConverter
import io.qalipsis.plugins.graphite.search.GraphiteQuery
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

@WithMockk
internal class GraphitePollStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<GraphitePollStepSpecificationConverter>() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    override fun `should not support unexpected spec`() {
        assertThat(converter.support(relaxedMockk<GraphitePollStepSpecificationImpl>()))
            .isTrue()
    }

    @Test
    override fun `should support expected spec`() {
        assertThat(converter.support(relaxedMockk()))
            .isFalse()
    }

    @Test
    @ExperimentalCoroutinesApi
    @Timeout(5)
    fun `should convert with name and metrics`() = testDispatcherProvider.runTest {
        // given
        val spec = GraphitePollStepSpecificationImpl()
        val queryBuilder = mockk<GraphiteQuery.() -> Unit>()
        spec.apply {
            this.name = "my-step"
            connect {
                server("http://localhost:2003")
            }
            query {
                withTarget("target.key")
            }

            monitoring {
                meters = true
                events = false
            }
            pollDelay(Duration.ofSeconds(10L))
            broadcast(123, Duration.ofSeconds(20))
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter, recordPrivateCalls = true)

        val recordsConverter: DatasourceObjectConverter<GraphiteQueryResult, out Any> = relaxedMockk()
        every { spiedConverter["buildConverter"]() } returns recordsConverter

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<GraphitePollStepSpecificationImpl>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(IterativeDatasourceStep::class).all {
                prop("name").isEqualTo("my-step")
                prop("processor").isNotNull().isInstanceOf(NoopDatasourceObjectProcessor::class)
                prop("converter").isSameAs(recordsConverter)
                prop("reader").isNotNull().isInstanceOf(GraphiteIterativeReader::class).all {
                    prop("pollStatement").isNotNull().isInstanceOf(GraphitePollStatement::class).all {
                        prop("graphiteQuery").typedProp<List<String>>("targets").containsOnly("target.key")
                    }
                    prop("meterRegistry").isEqualTo(meterRegistry)
                    prop("eventsLogger").isNull()
                }
            }
        }
        verifyOnce { spiedConverter["buildConverter"]() }

        val channelFactory = creationContext.createdStep!!
            .getProperty<GraphiteIterativeReader>("reader")
            .getProperty<() -> Channel<GraphiteQueryResult>>("resultsChannelFactory")
        val createdChannel = channelFactory()
        assertThat(createdChannel).all {
            transform { it.isEmpty }.isTrue()
            transform { it.isClosedForReceive }.isFalse()
            transform { it.isClosedForSend }.isFalse()
        }
    }

    @Test
    @ExperimentalCoroutinesApi
    @Timeout(5)
    fun `should convert without name and metrics`() = runBlockingTest {
        // given
        val spec = GraphitePollStepSpecificationImpl()
        spec.apply {
            connect {
                server("http://localhost:2003")
            }
            query {
                withTarget("target.key")
            }

            monitoring {
                meters = false
                events = false
            }
            pollDelay(Duration.ofSeconds(10L))
            broadcast(123, Duration.ofSeconds(20))
        }

        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter, recordPrivateCalls = true)

        val recordsConverter: DatasourceObjectConverter<GraphiteQueryResult, out Any> = relaxedMockk()
        every { spiedConverter["buildConverter"]() } returns recordsConverter

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<GraphitePollStepSpecificationImpl>
        )
        val createdStep = creationContext.createdStep
        // then
        createdStep!!.let {
            assertThat(it).isInstanceOf(IterativeDatasourceStep::class).all {
                prop("name").isEqualTo("")
                prop("processor").isNotNull().isInstanceOf(NoopDatasourceObjectProcessor::class)
                prop("converter").isSameAs(recordsConverter)
                prop("reader").isNotNull().isInstanceOf(GraphiteIterativeReader::class).all {
                    prop("pollStatement").isNotNull().isInstanceOf(GraphitePollStatement::class).all {
                        prop("graphiteQuery").typedProp<List<String>>("targets").containsOnly("target.key")
                    }
                    prop("meterRegistry").isNull()
                    prop("eventsLogger").isNull()
                }
            }
        }
        verifyOnce { spiedConverter["buildConverter"]() }

        val channelFactory = creationContext.createdStep!!
            .getProperty<GraphiteIterativeReader>("reader")
            .getProperty<() -> Channel<GraphiteQueryResult>>("resultsChannelFactory")
        val createdChannel = channelFactory()
        assertThat(createdChannel).all {
            transform { it.isEmpty }.isTrue()
            transform { it.isClosedForReceive }.isFalse()
            transform { it.isClosedForSend }.isFalse()
        }
    }

    @Test
    internal fun `should build batch converter`() {
        // when
        val converter =
            converter.invokeInvisible<DatasourceObjectConverter<GraphiteQueryResult, out Any>>("buildConverter")

        // then
        assertThat(converter).isInstanceOf(GraphitePollBatchConverter::class)
    }
}