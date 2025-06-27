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

package io.qalipsis.plugins.jakarta.consumer

import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import jakarta.jms.QueueConnection
import jakarta.jms.TopicConnection
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Krawist Ngoben
 */
@WithMockk
internal class JakartaConsumerIterativeReaderTest {

    @RelaxedMockK
    private lateinit var topicConnectionFactory: () -> TopicConnection

    @RelaxedMockK
    private lateinit var queueConnectionFactory: () -> QueueConnection

    @RelaxedMockK
    private lateinit var topicConnection: TopicConnection

    @RelaxedMockK
    private lateinit var queueConnection: QueueConnection

    private lateinit var reader: JakartaConsumerIterativeReader

    @BeforeEach
    fun initGlobal() {
        every { queueConnectionFactory() } returns queueConnection
        every { topicConnectionFactory() } returns topicConnection
    }

    @Test
    internal fun `should throw no exception if both connections are provided`() = testDispatcherProvider.runTest {
        reader = JakartaConsumerIterativeReader(
            "any",
            queues = listOf("queue-1", "queue-2"),
            queueConnectionFactory = queueConnectionFactory,
            topics = listOf("topic-1", "topic-2"),
            topicConnectionFactory = topicConnectionFactory
        )

        assertDoesNotThrow {
            reader.start(relaxedMockk())
        }
    }

    @Test
    internal fun `should throw no exception if both connections are not provided and both topics and queues are emtpy`() = testDispatcherProvider.runTest {
        reader = JakartaConsumerIterativeReader(
            "any",
            queues = listOf(),
            queueConnectionFactory = null,
            topics = listOf(),
            topicConnectionFactory = null
        )

        assertDoesNotThrow {
            reader.start(relaxedMockk())
        }
    }


    @Test
    internal fun `should throw exception if queues not provided for connection`() = testDispatcherProvider.runTest {
        reader = JakartaConsumerIterativeReader(
            "any",
            queues = listOf("queue-1", "queue-2"),
            queueConnectionFactory = null,
            topics = listOf("topic-1", "topic-2"),
            topicConnectionFactory = topicConnectionFactory
        )

        assertThrows<IllegalArgumentException> {
            reader.start(relaxedMockk())
        }
    }

    @Test
    internal fun `should throw exception if topics not provided for connection`() = testDispatcherProvider.runTest {
        reader = JakartaConsumerIterativeReader(
            "any",
            queues = listOf("queue-1", "queue-2"),
            queueConnectionFactory = queueConnectionFactory,
            topics = listOf("topic-1", "topic-2"),
            topicConnectionFactory = null
        )

        assertThrows<IllegalArgumentException> {
            reader.start(relaxedMockk())
        }
    }

    @Test
    internal fun `should throw exception if no connection provided`() = testDispatcherProvider.runTest {
        reader = JakartaConsumerIterativeReader(
            "any",
            queues = listOf("queue-1", "queue-2"),
            queueConnectionFactory = null,
            topics = listOf("topic-1", "topic-2"),
            topicConnectionFactory = null
        )

        assertThrows<IllegalArgumentException> {
            reader.start(relaxedMockk())
        }
    }

    companion object {
        @JvmField
        @RegisterExtension
        val testDispatcherProvider = TestDispatcherProvider()
    }
}
