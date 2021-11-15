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
