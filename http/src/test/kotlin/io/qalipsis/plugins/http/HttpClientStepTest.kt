/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 o the License, or
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

package io.qalipsis.plugins.http

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Throughput
import io.qalipsis.api.meters.Timer
import io.qalipsis.plugins.http.client.MonitoringResponseConsumer
import io.qalipsis.plugins.http.client.RequestMonitoringInterceptor
import io.qalipsis.plugins.http.connectionProvider.ConnectionProvider
import io.qalipsis.plugins.http.request.HttpRequest
import io.qalipsis.plugins.http.request.InternalHttpRequest
import io.qalipsis.plugins.http.request.SimpleHttpRequest
import io.qalipsis.plugins.http.response.DefaultHttpResponse
import io.qalipsis.plugins.http.response.HttpResponse
import io.qalipsis.plugins.http.response.ResponseConverter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.cancellation.CancellationException
import org.apache.hc.client5.http.async.HttpAsyncClient
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.http.nio.AsyncRequestProducer
import org.apache.hc.core5.http.nio.AsyncResponseConsumer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class HttpClientStepTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @MockK
    lateinit var responseConverter: ResponseConverter<String>

    @MockK
    private lateinit var connectionConfiguration: HttpClientConfiguration

    @MockK
    private lateinit var connectionProvider: ConnectionProvider

    @MockK
    private lateinit var eventsLogger: EventsLogger

    @MockK
    private lateinit var meterRegistry: CampaignMeterRegistry

    private fun stubMonitoring() {
        justRun { eventsLogger.info(any(), any(), any(), any<Map<String, String>>()) }
        justRun { eventsLogger.debug(any(), any(), any(), any<Map<String, String>>()) }
        justRun { eventsLogger.warn(any(), any(), any(), any<Map<String, String>>()) }

        every {
            meterRegistry.counter(
                scenarioName = any<String>(),
                stepName = any<String>(),
                name = any<String>(),
                tags = any<Map<String, String>>()
            )
        } returns mockk<Counter> {
            every { report(any()) } returns this
            justRun { increment() }
            justRun { increment(any()) }
        }
        every {
            meterRegistry.timer(
                scenarioName = any<String>(),
                stepName = any<String>(),
                name = any<String>(),
                tags = any<Map<String, String>>()
            )
        } returns mockk<Timer> {
            every { report(any()) } returns this
            justRun { record(any<Duration>()) }
        }
        every {
            meterRegistry.throughput(
                scenarioName = any<String>(),
                stepName = any<String>(),
                name = any<String>(),
                unit = any(),
                percentiles = any(),
                tags = any<Map<String, String>>()
            )
        } returns mockk<Throughput> {
            every { report(any()) } returns this
            justRun { record() }
            justRun { record(any<Double>()) }
        }
    }

    private fun stubContext(
        context: StepContext<String, HttpResponse<String>>,
        withMonitoring: Boolean = false,
    ) {
        if (withMonitoring) {
            every { context.toEventTags() } returns emptyMap()
            every { context.toMetersTags() } returns emptyMap()
            every { context.scenarioName } returns "test-scenario"
            every { context.stepName } returns "test-step"
        }
    }

    @Test
    fun `start should init connection provider`() = testDispatcherProvider.runTest {
        //given
        coJustRun { connectionProvider.init(any()) }
        val step = HttpClientStep<String, String>(
            id = "my-step",
            retryPolicy = null,
            ioCoroutineContext = this.coroutineContext,
            requestFactory = { _, _ -> mockk<HttpRequest<*>>() },
            clientConfiguration = connectionConfiguration,
            connectionProvider = connectionProvider,
            responseConverter = responseConverter,
            eventsLogger = eventsLogger,
            meterRegistry = meterRegistry
        )
        val startStopCtx = mockk<StepStartStopContext>()

        //when
        step.start(startStopCtx)

        //then
        coVerify(exactly = 1) { connectionProvider.init(startStopCtx) }
        confirmVerified(connectionProvider)
    }

    @Test
    fun `stop should shutdown connection provider`() = testDispatcherProvider.runTest {
        //given
        coJustRun { connectionProvider.shutdown(any()) }
        val step = HttpClientStep<String, String>(
            id = "my-step",
            retryPolicy = null,
            ioCoroutineContext = this.coroutineContext,
            requestFactory = { _, _ -> mockk<HttpRequest<*>>() },
            clientConfiguration = connectionConfiguration,
            connectionProvider = connectionProvider,
            responseConverter = responseConverter,
            eventsLogger = eventsLogger,
            meterRegistry = meterRegistry
        )
        val startStopCtx = mockk<StepStartStopContext>()

        //when
        step.stop(startStopCtx)

        //then
        coVerify(exactly = 1) { connectionProvider.shutdown(startStopCtx) }
        confirmVerified(connectionProvider)
    }

    @Test
    fun `should execute a request, return a result and release the client when successful`() =
        testDispatcherProvider.runTest {
            //given
            stubMonitoring()
            val context = mockk<StepContext<String, HttpResponse<String>>>()
            stubContext(context, withMonitoring = true)
            coEvery { context.receive() } returns "hello world"
            val httpClient = mockk<HttpAsyncClient>()
            every { connectionProvider.acquire(context) } returns httpClient
            coJustRun { connectionProvider.release(context, httpClient) }
            val asyncProducer = mockk<AsyncRequestProducer>()
            val internalRequest = mockk<InternalHttpRequest<SimpleHttpRequest, Any>>()
            every { internalRequest.toAsyncRequest(any()) } returns asyncProducer
            val defaultResponse = DefaultHttpResponse(
                reason = "Ok",
                code = 200,
                headers = emptyMap(),
                body = "Test body",
                bodyBytes = null,
                cookies = emptyMap(),
                contentType = null
            )
            every { responseConverter.convert(any(), any()) } returns defaultResponse
            val httpContextSlot = slot<HttpClientContext>()
            val callbackSlot = slot<FutureCallback<SimpleHttpResponse>>()
            every {
                httpClient.execute(
                    eq(asyncProducer),
                    any<AsyncResponseConsumer<SimpleHttpResponse>>(),
                    null,
                    capture(httpContextSlot),
                    capture(callbackSlot)
                )
            } answers {
                // Simulate what the interceptors and response consumer would set.
                val capturedContext = httpContextSlot.captured
                val now = System.nanoTime()
                capturedContext.setAttribute(RequestMonitoringInterceptor.SENT_ATTR, now)
                capturedContext.setAttribute(RequestMonitoringInterceptor.SENT_BYTES_ATTR, 0L)
                capturedContext.setAttribute(MonitoringResponseConsumer.FIRST_BYTE_ATTR, now + 1_000_000)
                capturedContext.setAttribute(MonitoringResponseConsumer.LAST_BYTE_ATTR, now + 2_000_000)

                val response = SimpleHttpResponse.create(200)
                callbackSlot.captured.completed(response)
                CompletableFuture.completedFuture(response)
            }
            val sentResponseSlot = slot<HttpResponse<String>>()
            coEvery { context.send(capture(sentResponseSlot)) } returns Unit
            val step = HttpClientStep<String, String>(
                id = "my-step",
                retryPolicy = null,
                ioCoroutineContext = this.coroutineContext,
                requestFactory = { _, _ -> internalRequest },
                clientConfiguration = connectionConfiguration,
                connectionProvider = connectionProvider,
                responseConverter = responseConverter,
                eventsLogger = eventsLogger,
                meterRegistry = meterRegistry
            )

            //when
            step.execute(context)

            //then
            coVerify(exactly = 1) { context.receive() }
            coVerify(exactly = 1) { connectionProvider.acquire(context) }
            verify(exactly = 1) { internalRequest.toAsyncRequest(connectionConfiguration) }
            coVerify(exactly = 1) { context.send(defaultResponse) }
            coVerify(exactly = 1) { connectionProvider.release(context, httpClient) }
            val actualResponse = sentResponseSlot.captured
            assertThat(actualResponse.reason).isEqualTo("Ok")
            assertThat(actualResponse.code).isEqualTo(200)
            assertThat(actualResponse.body).isEqualTo("Test body")

            // Verify monitoring events were recorded.
            verify { eventsLogger.info("http.apache.connecting", null, any(), any<Map<String, String>>()) }
            verify { eventsLogger.info("http.apache.connected", any<Duration>(), any(), any<Map<String, String>>()) }
            verify {
                eventsLogger.debug(
                    "http.apache.time-to-first-byte",
                    any<Duration>(),
                    any(),
                    any<Map<String, String>>()
                )
            }
            verify {
                eventsLogger.info(
                    "http.apache.received-response",
                    any<Array<Any?>>(),
                    any(),
                    any<Map<String, String>>()
                )
            }
            verify { eventsLogger.info("http.apache.status", 200, any(), any<Map<String, String>>()) }
        }

    @Test
    fun `should execute without monitoring when eventsLogger and meterRegistry are null`() =
        testDispatcherProvider.runTest {
            //given
            val context = mockk<StepContext<String, HttpResponse<String>>>()
            coEvery { context.receive() } returns "hello world"
            val httpClient = mockk<HttpAsyncClient>()
            every { connectionProvider.acquire(context) } returns httpClient
            coJustRun { connectionProvider.release(context, httpClient) }
            val asyncProducer = mockk<AsyncRequestProducer>()
            val internalRequest = mockk<InternalHttpRequest<SimpleHttpRequest, Any>>()
            every { internalRequest.toAsyncRequest(any()) } returns asyncProducer
            val defaultResponse = DefaultHttpResponse(
                reason = "Ok",
                code = 200,
                headers = emptyMap(),
                body = "Test body",
                bodyBytes = null,
                cookies = emptyMap(),
                contentType = null
            )
            every { responseConverter.convert(any(), any()) } returns defaultResponse
            val callbackSlot = slot<FutureCallback<SimpleHttpResponse>>()
            every {
                httpClient.execute(
                    eq(asyncProducer),
                    any<AsyncResponseConsumer<SimpleHttpResponse>>(),
                    null,
                    any(),
                    capture(callbackSlot)
                )
            } answers {
                val response = SimpleHttpResponse.create(200)
                callbackSlot.captured.completed(response)
                CompletableFuture.completedFuture(response)
            }
            val sentResponseSlot = slot<HttpResponse<String>>()
            coEvery { context.send(capture(sentResponseSlot)) } returns Unit
            val step = HttpClientStep<String, String>(
                id = "my-step",
                retryPolicy = null,
                ioCoroutineContext = this.coroutineContext,
                requestFactory = { _, _ -> internalRequest },
                clientConfiguration = connectionConfiguration,
                connectionProvider = connectionProvider,
                responseConverter = responseConverter,
                eventsLogger = null,
                meterRegistry = null
            )

            //when
            step.execute(context)

            //then
            coVerify(exactly = 1) { context.send(defaultResponse) }
            coVerify(exactly = 1) { connectionProvider.release(context, httpClient) }
            // No monitoring calls should have been made.
            verify(exactly = 0) { eventsLogger.info(any(), any(), any(), any<Map<String, String>>()) }
            verify(exactly = 0) { meterRegistry.counter(any(), any(), any(), any<Map<String, String>>()) }
        }

    @Test
    fun `execute should throw when http client callback fails and still release client`() =
        testDispatcherProvider.runTest {
            //given
            val context = mockk<StepContext<String, HttpResponse<String>>>()
            coEvery { context.receive() } returns "in"
            val httpClient = mockk<HttpAsyncClient>()
            every { connectionProvider.acquire(context) } returns httpClient
            coJustRun { connectionProvider.release(context, httpClient) }
            val asyncProducer = mockk<AsyncRequestProducer>()
            val internalRequest = mockk<InternalHttpRequest<SimpleHttpRequest, Any>>()
            every { internalRequest.toAsyncRequest(any()) } returns asyncProducer
            val callbackSlot = slot<FutureCallback<SimpleHttpResponse>>()
            every {
                httpClient.execute(
                    asyncProducer,
                    any(),
                    null,
                    any(),
                    capture(callbackSlot)
                )
            } answers {
                val exception = IllegalStateException("Request failed")
                callbackSlot.captured.failed(exception)
                CompletableFuture.failedFuture(exception)
            }
            val step = HttpClientStep<String, String>(
                id = "my-step",
                retryPolicy = null,
                ioCoroutineContext = this.coroutineContext,
                requestFactory = { _, _ -> internalRequest },
                clientConfiguration = mockk(),
                connectionProvider = connectionProvider,
                responseConverter = responseConverter,
                eventsLogger = null,
                meterRegistry = null
            )

            //when + then
            assertThrows<IllegalStateException> {
                step.execute(context)
            }
            coVerify(exactly = 1) { connectionProvider.release(context, httpClient) }
            coVerifyNever { context.send(any()) }
        }

    @Test
    fun `execute should record failure metrics when http client callback fails and monitoring is enabled`() =
        testDispatcherProvider.runTest {
            //given
            stubMonitoring()
            val context = mockk<StepContext<String, HttpResponse<String>>>()
            stubContext(context, withMonitoring = true)
            coEvery { context.receive() } returns "in"
            val httpClient = mockk<HttpAsyncClient>()
            every { connectionProvider.acquire(context) } returns httpClient
            coJustRun { connectionProvider.release(context, httpClient) }
            val asyncProducer = mockk<AsyncRequestProducer>()
            val internalRequest = mockk<InternalHttpRequest<SimpleHttpRequest, Any>>()
            every { internalRequest.toAsyncRequest(any()) } returns asyncProducer
            val callbackSlot = slot<FutureCallback<SimpleHttpResponse>>()
            every {
                httpClient.execute(
                    asyncProducer,
                    any(),
                    null,
                    any(),
                    capture(callbackSlot)
                )
            } answers {
                val exception = IllegalStateException("Connection refused")
                callbackSlot.captured.failed(exception)
                CompletableFuture.failedFuture(exception)
            }
            val step = HttpClientStep<String, String>(
                id = "my-step",
                retryPolicy = null,
                ioCoroutineContext = this.coroutineContext,
                requestFactory = { _, _ -> internalRequest },
                clientConfiguration = mockk(),
                connectionProvider = connectionProvider,
                responseConverter = responseConverter,
                eventsLogger = eventsLogger,
                meterRegistry = meterRegistry
            )

            //when + then
            assertThrows<IllegalStateException> {
                step.execute(context)
            }
            coVerify(exactly = 1) { connectionProvider.release(context, httpClient) }
            coVerifyNever { context.send(any()) }

            // Verify failure monitoring events were recorded.
            verify { eventsLogger.info("http.apache.connecting", null, any(), any<Map<String, String>>()) }
            // No SENT_ATTR was set -> connection failure should be recorded.
            verify {
                eventsLogger.warn(
                    "http.apache.connection-failure",
                    any<Array<Any?>>(),
                    any(),
                    any<Map<String, String>>()
                )
            }
            verify {
                eventsLogger.warn(
                    "http.apache.request-failure",
                    any<Array<Any?>>(),
                    any(),
                    any<Map<String, String>>()
                )
            }
        }

    @Test
    fun `execute should throw CancellationException when http client callback is cancelled and still release client`() =
        testDispatcherProvider.runTest {
            //given
            val context = mockk<StepContext<String, HttpResponse<String>>>()
            coEvery { context.receive() } returns "in"
            val httpClient = mockk<HttpAsyncClient>()
            every { connectionProvider.acquire(context) } returns httpClient
            coJustRun { connectionProvider.release(context, httpClient) }
            val asyncProducer = mockk<AsyncRequestProducer>()
            val internalRequest = mockk<InternalHttpRequest<SimpleHttpRequest, Any>>()
            every { internalRequest.toAsyncRequest(any()) } returns asyncProducer
            val callbackSlot = slot<FutureCallback<SimpleHttpResponse>>()
            every {
                httpClient.execute(
                    asyncProducer,
                    any(),
                    null,
                    any(),
                    capture(callbackSlot)
                )
            } answers {
                callbackSlot.captured.cancelled()
                CompletableFuture.failedFuture(
                    CancellationException("Cancelled")
                )
            }
            val step = HttpClientStep<String, String>(
                id = "my-step",
                retryPolicy = null,
                ioCoroutineContext = this.coroutineContext,
                requestFactory = { _, _ -> internalRequest },
                clientConfiguration = mockk(),
                connectionProvider = connectionProvider,
                responseConverter = responseConverter,
                eventsLogger = null,
                meterRegistry = null
            )

            //when + then
            assertThrows<CancellationException> {
                step.execute(context)
            }
            coVerify(exactly = 1) { connectionProvider.release(context, httpClient) }
            coVerifyNever { context.send(any()) }
        }

}
