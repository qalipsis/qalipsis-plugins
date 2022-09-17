/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.plugins.redis.lettuce.poll.scanners

import io.lettuce.core.ScanCursor
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.plugins.redis.lettuce.poll.PollRawResult
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 *
 * @author Gabriel Moraes
 */
@WithMockk
internal abstract class AbstractLettuceScannerTest<CURSOR : ScanCursor, RESULT : Any, EXECUTOR : AbstractLettuceScanner<CURSOR, RESULT>> {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    protected lateinit var clusterConnection: StatefulRedisClusterConnection<ByteArray, ByteArray>

    @RelaxedMockK
    protected lateinit var singleConnection: StatefulRedisConnection<ByteArray, ByteArray>

    @RelaxedMockK
    protected lateinit var cursor: CURSOR

    @RelaxedMockK
    protected lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    protected lateinit var resultsChannel: Channel<PollRawResult<*>>

    @InjectMockKs
    protected lateinit var scanner: EXECUTOR

    protected val tags: Map<String, String> = emptyMap()

    @BeforeEach
    internal fun setUp() {
        mockkStatic("io.qalipsis.api.sync.SuspendedFutureKt")
    }

    @AfterEach
    internal fun tearDown() {
        unmockkStatic("io.qalipsis.api.sync.SuspendedFutureKt")
    }

    @Test
    internal abstract fun `should not forward an empty result`()

    @Test
    internal abstract fun `should not return anything when an exception occurs`()

    @Test
    internal abstract fun `should execute on cluster connection until cursor is finished`()

    @Test
    internal abstract fun `should execute on single connection until cursor is finished`()

}
