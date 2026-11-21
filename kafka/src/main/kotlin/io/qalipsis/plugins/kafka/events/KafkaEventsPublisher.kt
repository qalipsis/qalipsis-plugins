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

package io.qalipsis.plugins.kafka.events

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.events.AbstractBufferedEventsPublisher
import io.qalipsis.api.events.Event
import io.qalipsis.api.events.EventLevel
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import jakarta.inject.Named
import jakarta.inject.Singleton
import java.time.Duration
import java.util.Properties
import io.qalipsis.plugins.kafka.producer.sendUntilFirstRejection
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.Serdes

/**
 * Implementation of [io.qalipsis.api.events.EventsPublisher] for Kafka.
 *
 * The events are buffered and sent asynchronously from the IO context, because [Producer.send] blocks when the broker
 * is not reachable or its buffer is full, which must not slow down the execution of the steps.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(beans = [KafkaEventsConfiguration::class])
internal class KafkaEventsPublisher(
    @Named(Executors.IO_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
    private val configuration: KafkaEventsConfiguration,
    private val meterRegistry: CampaignMeterRegistry,
    private val eventKafkaSerializer: EventKafkaSerializer,
) : AbstractBufferedEventsPublisher(
    // The levels are already filtered by the events logger.
    EventLevel.TRACE,
    configuration.lingerPeriod,
    configuration.batchSize,
    coroutineScope
) {

    private lateinit var producer: Producer<ByteArray, Event>

    private lateinit var counter: Counter

    /**
     * Parent of the running publications, joined when stopping.
     */
    private lateinit var publicationJobs: CompletableJob

    override fun start() {
        val actualProperties = Properties()
        actualProperties.putAll(configuration.configuration)
        actualProperties[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = configuration.bootstrap
        actualProperties[ProducerConfig.LINGER_MS_CONFIG] = configuration.lingerPeriod.toMillis().toString()
        actualProperties[ProducerConfig.BATCH_SIZE_CONFIG] = configuration.batchSize.toString()

        producer = KafkaProducer(actualProperties, Serdes.ByteArray().serializer(), eventKafkaSerializer)
        counter = meterRegistry.counter(EVENTS_EXPORT_TIMER_NAME)
        publicationJobs = SupervisorJob()
        super.start()
    }

    override fun stop() {
        // The buffered events are published when stopping, so the producer has to be closed afterwards.
        super.stop()
        runBlocking(coroutineScope.coroutineContext) {
            publicationJobs.complete()
            publicationJobs.join()
        }
        tryAndLogOrNull(log) {
            producer.flush()
            producer.close(PRODUCER_CLOSE_TIMEOUT)
        }
    }

    override suspend fun publish(values: List<Event>) {
        coroutineScope.launch(publicationJobs) {
            val sentCount = producer.sendUntilFirstRejection(configuration.topic, values) { exception ->
                if (exception == null) {
                    counter.increment()
                } else {
                    log.error(exception) { "An event could not be sent to Kafka: ${exception.message}" }
                }
            }
            if (sentCount < values.size) {
                log.error { "${values.size - sentCount} events were not sent to Kafka, because the broker rejected them" }
            }
        }
    }

    companion object {

        private const val EVENTS_EXPORT_TIMER_NAME = "kafka.events.export"

        private val PRODUCER_CLOSE_TIMEOUT = Duration.ofSeconds(10)

        @JvmStatic
        private val log = logger()
    }
}
