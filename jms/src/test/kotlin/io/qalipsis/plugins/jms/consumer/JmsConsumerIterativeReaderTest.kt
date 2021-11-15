package io.qalipsis.plugins.jms.consumer;

import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.jms.QueueConnection
import javax.jms.TopicConnection

/**
 *
 * @author Alexander Sosnovsky
 */
@WithMockk
internal class JmsConsumerIterativeReaderTest {

    @RelaxedMockK
    private lateinit var topicConnectionFactory: () -> TopicConnection

    @RelaxedMockK
    private lateinit var queueConnectionFactory: () -> QueueConnection

    @RelaxedMockK
    private lateinit var topicConnection: TopicConnection

    @RelaxedMockK
    private lateinit var queueConnection: QueueConnection

    private lateinit var reader: JmsConsumerIterativeReader

    @BeforeEach
    fun initGlobal() {
        every { queueConnectionFactory() } returns queueConnection
        every { topicConnectionFactory() } returns topicConnection
    }

    @Test
    internal fun `should throw exception if both connection are provided`() = runBlockingTest {
        reader = JmsConsumerIterativeReader(
                "any",
                queues = listOf(),
                queueConnectionFactory = queueConnectionFactory,
                topics = listOf(),
                topicConnectionFactory = topicConnectionFactory
        )

        assertThrows<IllegalArgumentException> {
            reader.start(relaxedMockk())
        }
    }

    @Test
    internal fun `should throw exception if queues not provided for connection`() = runBlockingTest {
        reader = JmsConsumerIterativeReader(
            "any",
            queues = listOf(),
            queueConnectionFactory = queueConnectionFactory,
            topics = listOf(),
            topicConnectionFactory = null
        )

        assertThrows<IllegalArgumentException> {
            reader.start(relaxedMockk())
        }
    }

    @Test
    internal fun `should throw exception if topics not provided for connection`() = runBlockingTest {
        reader = JmsConsumerIterativeReader(
            "any",
            queues = listOf(),
            queueConnectionFactory = null,
            topics = listOf(),
            topicConnectionFactory = topicConnectionFactory
        )

        assertThrows<IllegalArgumentException> {
            reader.start(relaxedMockk())
        }
    }

    @Test
    internal fun `should throw exception if no connection provided`() = runBlockingTest {
        reader = JmsConsumerIterativeReader(
            "any",
            queues = listOf(),
            queueConnectionFactory = null,
            topics = listOf(),
            topicConnectionFactory = null
        )

        assertThrows<IllegalArgumentException> {
            reader.start(relaxedMockk())
        }
    }
}
