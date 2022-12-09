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
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Krawist Ngoben
 */
@WithMockk
internal class JakartaConsumerIterativeReaderTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

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
    internal fun `should throw exception if both connection are provided`() = testDispatcherProvider.runTest {
        reader = JakartaConsumerIterativeReader(
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
    internal fun `should throw exception if queues not provided for connection`() = testDispatcherProvider.runTest {
        reader = JakartaConsumerIterativeReader(
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
    internal fun `should throw exception if topics not provided for connection`() = testDispatcherProvider.runTest {
        reader = JakartaConsumerIterativeReader(
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
    internal fun `should throw exception if no connection provided`() = testDispatcherProvider.runTest {
        reader = JakartaConsumerIterativeReader(
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
