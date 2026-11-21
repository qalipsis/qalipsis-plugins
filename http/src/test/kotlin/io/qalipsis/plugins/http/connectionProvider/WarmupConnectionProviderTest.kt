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

package io.qalipsis.plugins.http.connectionProvider

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.HttpConnectionException
import io.qalipsis.plugins.http.connectionProvider.DedicatedConnectionManager.DedicatedConnection
import io.qalipsis.plugins.http.connectionProvider.impl.WarmupConnectionProvider
import io.qalipsis.plugins.http.connectionProvider.impl.catadioptre.dedicatedClient
import io.qalipsis.plugins.http.connectionProvider.impl.catadioptre.freeConnections
import io.qalipsis.plugins.http.connectionProvider.impl.catadioptre.initialized
import io.qalipsis.plugins.http.connectionProvider.impl.catadioptre.minionConnections
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.reactor.IOReactorStatus.SHUT_DOWN
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class WarmupConnectionProviderTest {

    private lateinit var server: MockWebServer

    private lateinit var clientConfiguration: HttpClientConfiguration

    private val stepStartStopContext = mockk<StepStartStopContext> { every { scheduledMinionCount } returns 2 }

    private val minion1 = minionContext("minion-1")

    private val minion2 = minionContext("minion-2")

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        clientConfiguration = HttpClientConfiguration().apply {
            url(server.url("/").toString())
            connectTimeout = Duration.ofSeconds(2)
            ioThreadCount = 1
        }
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `given scheduled minions when initializing then one connection per minion is opened on a single client`() {
        // given
        every { stepStartStopContext.scheduledMinionCount } returns MINIONS_ABOVE_PRECONNECTION_BATCH
        val provider = WarmupConnectionProvider(shared = false, httpClientConfiguration = clientConfiguration)

        // when
        provider.init(stepStartStopContext)

        // then
        assertThat(provider.dedicatedClient().connections).all {
            hasSize(MINIONS_ABOVE_PRECONNECTION_BATCH)
            each { it.prop(DedicatedConnection::connectedOnce).transform { flag -> flag.get() }.isTrue() }
        }
        assertThat(provider.freeConnections()).hasSize(MINIONS_ABOVE_PRECONNECTION_BATCH)
        assertThat(provider.acquire(minion1)).isSameInstanceAs(provider.acquire(minion2))
        provider.shutdown(stepStartStopContext)
    }

    @Test
    fun `given two minions when they send requests alternately then each always uses its own connection`() {
        // given
        repeat(REQUESTS_PER_MINION * 2) { server.enqueue(MockResponse().setResponseCode(200)) }
        val provider = WarmupConnectionProvider(shared = false, httpClientConfiguration = clientConfiguration)
        provider.init(stepStartStopContext)

        // when
        val ports = (1..REQUESTS_PER_MINION).flatMap {
            listOf(minion1 to localPort(send(provider, minion1)), minion2 to localPort(send(provider, minion2)))
        }.groupBy({ it.first.minionId }, { it.second })

        // then
        assertThat(ports.getValue("minion-1").distinct()).hasSize(1)
        assertThat(ports.getValue("minion-2").distinct()).hasSize(1)
        assertThat(ports.getValue("minion-1").first()).isNotEqualTo(ports.getValue("minion-2").first())
        // The sequence number is the index of the request on its connection, as seen by the server.
        val sequenceNumbers = (1..REQUESTS_PER_MINION * 2).map { server.takeRequest(1, TimeUnit.SECONDS)!!.sequenceNumber }
        assertThat(sequenceNumbers).isEqualTo((0 until REQUESTS_PER_MINION).flatMap { listOf(it, it) })
        provider.shutdown(stepStartStopContext)
    }

    @Test
    fun `given a connection closed by the server when the minion sends its next request then it is reopened for it`() {
        // given
        server.enqueue(MockResponse().setResponseCode(200).addHeader("Connection", "close"))
        server.enqueue(MockResponse().setResponseCode(200))
        val provider = WarmupConnectionProvider(shared = false, httpClientConfiguration = clientConfiguration)
        provider.init(stepStartStopContext)
        val firstContext = send(provider, minion1)

        // when
        val secondContext = send(provider, minion1)

        // then
        assertThat(firstContext.getAttribute(DedicatedConnectionManager.RECONNECTION_ATTR)).isNull()
        assertThat(secondContext.getAttribute(DedicatedConnectionManager.RECONNECTION_ATTR)).isEqualTo(true)
        assertThat(localPort(secondContext)).isNotEqualTo(localPort(firstContext))
        assertThat(provider.minionConnections()[minion1.minionId]).isSameInstanceAs(secondContext.userToken)
        provider.shutdown(stepStartStopContext)
    }

    @Test
    fun `given a minion executed several times when it sends requests then it always keeps its own connection`() {
        // given
        repeat(EXECUTIONS * 2) { server.enqueue(MockResponse().setResponseCode(200)) }
        val provider = WarmupConnectionProvider(shared = false, httpClientConfiguration = clientConfiguration)
        provider.init(stepStartStopContext)
        // The end of each execution of the step by the minion, before it is replayed with the same identifier.
        val completingMinion1 = minionContext("minion-1", isTail = true)

        // when
        val ports = (1..EXECUTIONS).map {
            localPort(send(provider, completingMinion1)).also { send(provider, minion2) }
        }

        // then
        assertThat(ports.distinct()).hasSize(1)
        assertThat(provider.minionConnections().keys).isEqualTo(setOf("minion-1", "minion-2"))
        assertThat(provider.freeConnections()).hasSize(0)
        provider.shutdown(stepStartStopContext)
    }

    @Test
    fun `given more minions than scheduled when they send requests then the missing connections are opened on demand`() {
        // given
        every { stepStartStopContext.scheduledMinionCount } returns 1
        repeat(2) { server.enqueue(MockResponse().setResponseCode(200)) }
        val provider = WarmupConnectionProvider(shared = false, httpClientConfiguration = clientConfiguration)
        provider.init(stepStartStopContext)

        // when
        val port1 = localPort(send(provider, minion1))
        val port2 = localPort(send(provider, minion2))

        // then
        assertThat(port2).isNotEqualTo(port1)
        assertThat(provider.minionConnections()).hasSize(2)
        provider.shutdown(stepStartStopContext)
    }

    @Test
    fun `given an initialized provider when shutting down then the client and its connections are released`() {
        // given
        val provider = WarmupConnectionProvider(shared = false, httpClientConfiguration = clientConfiguration)
        provider.init(stepStartStopContext)
        val client = provider.acquire(minion1)
        provider.prepare(minion1, HttpClientContext.create())

        // when
        provider.shutdown(stepStartStopContext)

        // then
        assertThat(client.status).isEqualTo(SHUT_DOWN)
        assertThat(provider.initialized().get()).isFalse()
        assertThat(provider.freeConnections()).hasSize(0)
        assertThat(provider.minionConnections()).hasSize(0)
    }

    @Test
    fun `given an unreachable server when initializing then the start fails`() {
        // given
        clientConfiguration.url("http://localhost:$PORT_WITHOUT_SERVER")
        val provider = WarmupConnectionProvider(shared = false, httpClientConfiguration = clientConfiguration)

        // when
        val exception = assertThrows<HttpConnectionException> { provider.init(stepStartStopContext) }

        // then
        assertThat(exception.message!!).contains("http://localhost:$PORT_WITHOUT_SERVER")
        assertThat(provider.initialized().get()).isFalse()
    }

    @Test
    fun `given an unreachable socks proxy when initializing then the start fails`() {
        // given
        val configuration = clientConfiguration.copy(
            socksProxyAddress = "localhost:$PORT_WITHOUT_SERVER",
            socksProxyUsername = "user",
            socksProxyPassword = "pass"
        ).apply { url(server.url("/").toString()) }
        val provider = WarmupConnectionProvider(shared = false, httpClientConfiguration = configuration)

        // when + then
        assertThrows<HttpConnectionException> { provider.init(stepStartStopContext) }
    }

    /**
     * Sends a request for the minion of [context] and returns the context of the request once it is complete.
     */
    private fun send(provider: WarmupConnectionProvider, context: StepContext<*, *>): HttpClientContext {
        val client = provider.acquire(context)
        val httpContext = HttpClientContext.create()
        provider.prepare(context, httpContext)
        val response = CompletableFuture<SimpleHttpResponse>()
        client.execute(SimpleHttpRequest.create("GET", server.url("/").toString()), httpContext, response.asCallback())
        assertThat(response.get(2, TimeUnit.SECONDS).code).isEqualTo(200)
        provider.release(context, client)
        return httpContext
    }

    private fun localPort(httpContext: HttpClientContext) = (httpContext.endpointDetails.localAddress as InetSocketAddress).port

    private fun minionContext(minionId: String, isTail: Boolean = false) = mockk<StepContext<*, *>> {
        every { this@mockk.minionId } returns minionId
        every { this@mockk.isTail } returns isTail
    }

    private fun CompletableFuture<SimpleHttpResponse>.asCallback() = object : FutureCallback<SimpleHttpResponse> {
        override fun completed(result: SimpleHttpResponse) {
            complete(result)
        }

        override fun failed(ex: Exception) {
            completeExceptionally(ex)
        }

        override fun cancelled() {
            cancel(true)
        }
    }

    private companion object {

        /**
         * Nothing listens there, so the connections to that port cannot be established.
         */
        const val PORT_WITHOUT_SERVER = 1

        /**
         * Count of minions requiring more than one batch of pre-connections.
         */
        const val MINIONS_ABOVE_PRECONNECTION_BATCH = 150

        const val REQUESTS_PER_MINION = 3

        const val EXECUTIONS = 3
    }
}
