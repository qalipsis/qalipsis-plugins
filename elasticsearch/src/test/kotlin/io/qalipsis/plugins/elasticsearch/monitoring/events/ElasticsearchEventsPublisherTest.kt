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

package io.qalipsis.plugins.elasticsearch.monitoring.events

import assertk.assertThat
import assertk.assertions.isTrue
import io.aerisconsulting.catadioptre.setProperty
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.qalipsis.api.events.Event
import io.qalipsis.api.events.EventLevel
import io.qalipsis.plugins.elasticsearch.monitoring.ElasticsearchOperationsImpl
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@Timeout(10)
internal class ElasticsearchEventsPublisherTest {

    private val operations = mockk<ElasticsearchOperationsImpl>(relaxed = true)

    private val publisher = ElasticsearchEventsPublisher(
        CoroutineScope(Dispatchers.Default),
        Dispatchers.Default,
        ElasticsearchEventsConfiguration().apply { lingerPeriod = Duration.ofMinutes(1) },
        mockk(relaxed = true),
        mockk(relaxed = true)
    ).also { it.setProperty("elasticsearchOperations", operations) }

    @Test
    fun `given a running publication when stopping then the publication is completed first`() {
        val completed = AtomicBoolean(false)
        coEvery { operations.executeBulk(any(), any(), any(), any(), any(), any()) } coAnswers {
            delay(PUBLICATION_DURATION_MS)
            completed.set(true)
        }
        publisher.start()

        runBlocking { publisher.publish(listOf(Event("my-event", EventLevel.INFO))) }
        publisher.stop()

        assertThat(completed.get()).isTrue()
    }

    @Test
    fun `given a publication failing with an error when stopping then the stop is not blocked`() {
        coEvery { operations.executeBulk(any(), any(), any(), any(), any(), any()) } throws OutOfMemoryError()
        publisher.start()

        runBlocking { publisher.publish(listOf(Event("my-event", EventLevel.INFO))) }
        publisher.stop()

        coVerify { operations.executeBulk(any(), any(), any(), any(), any(), any()) }
    }

    private companion object {

        const val PUBLICATION_DURATION_MS = 500L
    }
}
