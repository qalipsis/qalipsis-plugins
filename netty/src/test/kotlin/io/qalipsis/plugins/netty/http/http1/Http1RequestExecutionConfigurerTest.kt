/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.plugins.netty.http.http1

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPipeline
import io.netty.channel.EventLoop
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.qalipsis.api.sync.ImmutableSlot
import io.qalipsis.plugins.netty.http.client.monitoring.HttpStepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.http.request.InternalHttpRequest
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class Http1RequestExecutionConfigurerTest {

    private lateinit var eventLoop: EventLoop

    private lateinit var responseHandler: CapturingSlot<ChannelHandler>

    private lateinit var configurer: Http1RequestExecutionConfigurer

    @BeforeEach
    fun setUp() {
        eventLoop = mockk(relaxed = true)
        val channel = mockk<Channel> { every { eventLoop() } returns eventLoop }
        responseHandler = slot()
        val pipeline = mockk<ChannelPipeline>(relaxed = true) {
            every { channel() } returns channel
            every { addLast(any<String>(), capture(responseHandler)) } returns this
        }
        configurer = Http1RequestExecutionConfigurer(HttpClientConfiguration(), pipeline)
    }

    @Test
    fun `given a channel with handlers when a new request is configured outside of the event loop then the handlers are reset on the event loop`() {
        // given
        every { eventLoop.inEventLoop() } returns false
        val previousSlot = ImmutableSlot<Result<HttpResponse>>()
        configurer.configure(request(), collector(), previousSlot)
        val reset = slot<Runnable>()
        every { eventLoop.execute(capture(reset)) } returns Unit

        // when
        val nextSlot = ImmutableSlot<Result<HttpResponse>>()
        configurer.configure(request(), collector(), nextSlot)
        receiveResponse()

        // then
        verify { eventLoop.execute(any()) }
        assertThat(previousSlot.isEmpty()).isFalse()
        assertThat(nextSlot.isEmpty()).isTrue()

        // when
        reset.captured.run()
        receiveResponse()

        // then
        assertThat(nextSlot.isEmpty()).isFalse()
    }

    @Test
    fun `given a channel with handlers when a new request is configured on the event loop then the handlers are reset at once`() {
        // given
        every { eventLoop.inEventLoop() } returns true
        configurer.configure(request(), collector(), ImmutableSlot())

        // when
        val nextSlot = ImmutableSlot<Result<HttpResponse>>()
        configurer.configure(request(), collector(), nextSlot)
        receiveResponse()

        // then
        verify(exactly = 0) { eventLoop.execute(any()) }
        assertThat(nextSlot.isEmpty()).isFalse()
    }

    private fun request() = mockk<InternalHttpRequest<*, Any>> {
        every { toNettyRequest(any()) } returns mockk<io.netty.handler.codec.http.HttpRequest>(relaxed = true)
    } as io.qalipsis.plugins.netty.http.request.HttpRequest<*>

    private fun collector() = mockk<HttpStepContextBasedSocketMonitoringCollector>(relaxed = true)

    private fun receiveResponse() {
        val context = mockk<ChannelHandlerContext>(relaxed = true)
        (responseHandler.captured as Http1ResponseHandler).channelRead(
            context,
            DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED)
        )
    }
}
