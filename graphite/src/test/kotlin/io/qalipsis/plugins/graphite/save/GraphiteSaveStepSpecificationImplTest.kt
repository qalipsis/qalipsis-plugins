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

package io.qalipsis.plugins.graphite.save

import assertk.all
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.mockk.mockk
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.DummyStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.graphite.GraphiteConnectionSpecificationImpl
import io.qalipsis.plugins.graphite.GraphiteProtocol
import io.qalipsis.plugins.graphite.client.GraphiteRecord
import io.qalipsis.plugins.graphite.graphite
import io.qalipsis.test.coroutines.TestDispatcherProvider
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.random.Random
import kotlin.reflect.KClass


/**
 *
 * @author Palina Bril
 */
internal class GraphiteSaveStepSpecificationImplTest {

    private val recordSupplier: (suspend (ctx: StepContext<*, *>, input: Any?) -> List<GraphiteRecord>) = { _, _ ->
        listOf(
            GraphiteRecord("foo", value = 1.1),
            GraphiteRecord("foo", value = 1.2),
            GraphiteRecord("foo", value = 1.3)
        )
    }

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    fun `should add minimal configuration for the step`() = testDispatcherProvider.runTest {
        // given
        val previousStep = DummyStepSpecification()

        // when
        previousStep.graphite().save {
            records(recordSupplier)
        }

        // then
        assertThat(previousStep.nextSteps[0]).isInstanceOf(GraphiteSaveStepSpecificationImpl::class).all {
            prop(GraphiteSaveStepSpecificationImpl<*>::name).isEmpty()
            prop(GraphiteSaveStepSpecificationImpl<*>::retryPolicy).isNull()
            prop(GraphiteSaveStepSpecificationImpl<*>::connectionConfig).all {
                prop(GraphiteConnectionSpecificationImpl::host).isEqualTo("localhost")
                prop(GraphiteConnectionSpecificationImpl::port).isEqualTo(2003)
                prop(GraphiteConnectionSpecificationImpl::protocol).isEqualTo(GraphiteProtocol.PLAINTEXT)
                prop(GraphiteConnectionSpecificationImpl::nettyChannelClass).isEqualTo(NioSocketChannel::class)
                prop(GraphiteConnectionSpecificationImpl::nettyWorkerGroup).transform { it.invoke() }
                    .isInstanceOf<NioEventLoopGroup>()
            }
            prop(GraphiteSaveStepSpecificationImpl<*>::records).isEqualTo(recordSupplier)
            prop(GraphiteSaveStepSpecificationImpl<*>::monitoringConfig).isNotNull().all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isFalse()
            }
        }
    }

    @Test
    fun `should add minimal configuration for the step with events`() = testDispatcherProvider.runTest {
        // given
        val previousStep = DummyStepSpecification()

        // when
        previousStep.graphite().save {
            records(recordSupplier)
            monitoring { events = true }
        }

        // then
        assertThat(previousStep.nextSteps[0]).isInstanceOf(GraphiteSaveStepSpecificationImpl::class).all {
            prop(GraphiteSaveStepSpecificationImpl<*>::name).isEmpty()
            prop(GraphiteSaveStepSpecificationImpl<*>::retryPolicy).isNull()
            prop(GraphiteSaveStepSpecificationImpl<*>::connectionConfig).all {
                prop(GraphiteConnectionSpecificationImpl::host).isEqualTo("localhost")
                prop(GraphiteConnectionSpecificationImpl::port).isEqualTo(2003)
                prop(GraphiteConnectionSpecificationImpl::protocol).isEqualTo(GraphiteProtocol.PLAINTEXT)
                prop(GraphiteConnectionSpecificationImpl::nettyChannelClass).isEqualTo(NioSocketChannel::class)
                prop(GraphiteConnectionSpecificationImpl::nettyWorkerGroup).transform { it.invoke() }
                    .isInstanceOf<NioEventLoopGroup>()
            }
            prop(GraphiteSaveStepSpecificationImpl<*>::records).isEqualTo(recordSupplier)
            prop(GraphiteSaveStepSpecificationImpl<*>::monitoringConfig).isNotNull().all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isFalse()
            }
        }
    }

    @Test
    fun `should add a complete configuration for the step with meters`() = testDispatcherProvider.runTest {
        // given
        val previousStep = DummyStepSpecification()
        val stepName = RandomStringUtils.randomAlphanumeric(10)
        val server = RandomStringUtils.randomAlphanumeric(10)
        val port = Random(10000).nextInt()
        val channelClass = mockk<KClass<out SocketChannel>>()
        val workerGroup = mockk<NioEventLoopGroup>()
        val retryPolicy = mockk<RetryPolicy>()

        // when
        previousStep.graphite().save {
            name = stepName
            records(recordSupplier)
            connect {
                server(server, port)
                protocol(GraphiteProtocol.PICKLE)
                netty(channelClass) { workerGroup }
            }
            monitoring {
                meters = true
                events = false
            }
        }.retry(retryPolicy)

        // then
        assertThat(previousStep.nextSteps[0]).isInstanceOf(GraphiteSaveStepSpecificationImpl::class).all {
            prop(GraphiteSaveStepSpecificationImpl<*>::name).isEqualTo(stepName)
            prop(GraphiteSaveStepSpecificationImpl<*>::retryPolicy).isSameAs(retryPolicy)
            prop(GraphiteSaveStepSpecificationImpl<*>::connectionConfig).all {
                prop(GraphiteConnectionSpecificationImpl::host).isEqualTo(server)
                prop(GraphiteConnectionSpecificationImpl::port).isEqualTo(port)
                prop(GraphiteConnectionSpecificationImpl::protocol).isEqualTo(GraphiteProtocol.PICKLE)
                prop(GraphiteConnectionSpecificationImpl::nettyChannelClass).isSameAs(channelClass)
                prop(GraphiteConnectionSpecificationImpl::nettyWorkerGroup).transform { it.invoke() }
                    .isSameAs(workerGroup)
            }
            prop(GraphiteSaveStepSpecificationImpl<*>::records).isEqualTo(recordSupplier)
            prop(GraphiteSaveStepSpecificationImpl<*>::monitoringConfig).isNotNull().all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }
}
