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

package io.qalipsis.plugins.netty.socket

import assertk.assertThat
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import io.qalipsis.test.mockk.WithMockk
import java.time.Duration
import org.junit.jupiter.api.Test

@WithMockk
internal class ConnectionMonitoringCollectorTest {

    @RelaxedMockK
    private lateinit var stepCollector: SocketMonitoringCollector

    @Test
    fun `given a step collector with a previous failure when opening a new connection then there is no cause`() {
        // given
        every { stepCollector.cause } returns RuntimeException("Failure of another connection")

        // when
        val collector = ConnectionMonitoringCollector(stepCollector)

        // then
        assertThat(collector.cause).isNull()
    }

    @Test
    fun `given a connection when it fails then the failure is kept and forwarded`() {
        // given
        val collector = ConnectionMonitoringCollector(stepCollector)
        val failure = RuntimeException("Connection refused")

        // when
        collector.recordConnectionFailure(Duration.ofMillis(12), failure)

        // then
        assertThat(collector.cause).isSameInstanceAs(failure)
        verify { stepCollector.recordConnectionFailure(Duration.ofMillis(12), refEq(failure)) }
    }

    @Test
    fun `given a connection when the TLS handshake fails then the failure is kept and forwarded`() {
        // given
        val collector = ConnectionMonitoringCollector(stepCollector)
        val failure = RuntimeException("Handshake failure")

        // when
        collector.recordTlsHandshakeFailure(Duration.ofMillis(12), failure)

        // then
        assertThat(collector.cause).isSameInstanceAs(failure)
        verify { stepCollector.recordTlsHandshakeFailure(Duration.ofMillis(12), refEq(failure)) }
    }

    @Test
    fun `given a connection when it succeeds then the records are forwarded`() {
        // given
        val collector = ConnectionMonitoringCollector(stepCollector)

        // when
        collector.recordConnecting()
        collector.recordConnected(Duration.ofMillis(3))

        // then
        assertThat(collector.cause).isNull()
        verify {
            stepCollector.recordConnecting()
            stepCollector.recordConnected(Duration.ofMillis(3))
        }
    }
}
