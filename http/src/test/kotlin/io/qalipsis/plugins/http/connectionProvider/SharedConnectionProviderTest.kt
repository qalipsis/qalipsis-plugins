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

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.justRun
import io.mockk.verify
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.test.mockk.WithMockk
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.junit.jupiter.api.Test

@WithMockk
internal class SharedConnectionProviderTest {

    @MockK
    private lateinit var delegate: ConnectionProvider

    @RelaxedMockK
    private lateinit var startStopContext: StepStartStopContext

    @RelaxedMockK
    private lateinit var stepContext: StepContext<*, *>

    @RelaxedMockK
    private lateinit var client: CloseableHttpAsyncClient

    @Test
    fun `given two steps when they start then the delegate is only initialized once`() {
        // given
        justRun { delegate.init(any()) }
        val provider = SharedConnectionProvider(delegate)

        // when
        provider.init(startStopContext)
        provider.init(startStopContext)

        // then
        verify(exactly = 1) { delegate.init(startStopContext) }
        confirmVerified(delegate)
    }

    @Test
    fun `given two started steps when they stop then the delegate is only shut down by the last one`() {
        // given
        justRun { delegate.init(any()) }
        justRun { delegate.shutdown(any()) }
        val provider = SharedConnectionProvider(delegate)
        provider.init(startStopContext)
        provider.init(startStopContext)

        // when
        provider.shutdown(startStopContext)

        // then
        verify(exactly = 0) { delegate.shutdown(any()) }

        // when
        provider.shutdown(startStopContext)
        provider.shutdown(startStopContext)

        // then
        verify(exactly = 1) { delegate.shutdown(startStopContext) }
    }

    @Test
    fun `given a stopped provider when a step starts again then the delegate is initialized again`() {
        // given
        justRun { delegate.init(any()) }
        justRun { delegate.shutdown(any()) }
        val provider = SharedConnectionProvider(delegate)
        provider.init(startStopContext)
        provider.shutdown(startStopContext)

        // when
        provider.init(startStopContext)

        // then
        verify(exactly = 2) { delegate.init(startStopContext) }
    }

    @Test
    fun `given a shared provider when a request is executed then the calls are forwarded to the delegate`() {
        // given
        val httpContext = HttpClientContext.create()
        every { delegate.acquire(any()) } returns client
        justRun { delegate.prepare(any(), any()) }
        justRun { delegate.release(any(), any()) }
        val provider = SharedConnectionProvider(delegate)

        // when
        val acquired = provider.acquire(stepContext)
        provider.prepare(stepContext, httpContext)
        provider.release(stepContext, acquired)

        // then
        verify {
            delegate.acquire(stepContext)
            delegate.prepare(stepContext, refEq(httpContext))
            delegate.release(stepContext, refEq(client))
        }
        confirmVerified(delegate)
    }
}
