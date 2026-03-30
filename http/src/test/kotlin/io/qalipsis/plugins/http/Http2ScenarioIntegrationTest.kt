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
