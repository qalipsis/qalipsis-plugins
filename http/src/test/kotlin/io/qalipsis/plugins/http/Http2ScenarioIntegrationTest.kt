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

package io.qalipsis.plugins.http

import io.micronaut.core.io.socket.SocketUtils
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.runtime.test.QalipsisTestRunner
import okhttp3.Protocol
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 *
 * @author Francisca Eze
 */
@Timeout(180)
class Http2ScenarioIntegrationTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    internal fun setUp() {
        server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.protocols = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
        if (serverPort == -1) {
            serverPort = SocketUtils.findAvailableTcpPort()
        }
        Http2Scenario.httpPort = serverPort
        server.start(serverPort)
    }

    @AfterEach
    internal fun tearDown() {
        server.shutdown()
    }

    @Test
    internal fun `should run the HTTP scenario`() {
        // given
        val expectedHttpRequests = Http2Scenario.MINIONS * (Http2Scenario.REPEAT)
        enqueueOkResponses(expectedHttpRequests.toInt())

        // when
        val exitCode =
            QalipsisTestRunner.withScenarios("apache-http-2-scenario").withEnvironments("scenario").execute()

        // then
        Assertions.assertEquals(0, exitCode)

        val httpCount = server.requestCount.toLong()
        log.info { "$httpCount HTTP requests" }

        Assertions.assertEquals(expectedHttpRequests, httpCount)
    }

    @Test
    internal fun `should run the HTTP scenario with pooling`() {
        // given
        val expectedHttpRequests = Http2Scenario.POOLED_MINIONS * (Http2Scenario.REPEAT)
        enqueueOkResponses(expectedHttpRequests.toInt())

        // when
        val exitCode =
            QalipsisTestRunner.withScenarios("apache-http-2-pooled-scenario").withEnvironments("scenario").execute()

        // then
        Assertions.assertEquals(0, exitCode)
        val httpCount = server.requestCount.toLong()
        log.info { "$httpCount HTTP requests" }
        Assertions.assertEquals(expectedHttpRequests, httpCount)
    }

    private fun enqueueOkResponses(count: Int) {
        repeat(count) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/plain")
                    .setBody("OK")
            )
        }
    }

    companion object {

        private var serverPort = -1

        private val serverCertificates: HandshakeCertificates by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            val localhostCertificate = HeldCertificate.Builder()
                .addSubjectAlternativeName("localhost")
                .build()

            HandshakeCertificates.Builder()
                .heldCertificate(localhostCertificate)
                .build()
        }

        private val log = logger()
    }
}
