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

package io.qalipsis.plugins.kafka.meters

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.MeasurementPublisher
import io.qalipsis.api.meters.MeterSnapshot
import java.time.Duration
import io.qalipsis.plugins.kafka.producer.sendUntilFirstRejection
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.Serializer

/**
 * Measurement publisher for Apache Kafka.
 *
 * The meters are sent asynchronously from the IO context, because [Producer.send] blocks when the broker
 * is not reachable or its buffer is full.
 *
 * @author Palina Bril
 */
@Requires(beans = [KafkaMeterConfig::class])
internal class KafkaMeasurementPublisher(
    private val config: KafkaMeterConfig,
    private val serializer: Serializer<MeterSnapshot>,
    private val ioScope: CoroutineScope,
) : MeasurementPublisher {

    private lateinit var producer: Producer<ByteArray, MeterSnapshot>

    private val topic = config.topic

    /**
     * Parent of the running publications, joined when stopping.
     */
    private lateinit var publicationJobs: CompletableJob

    override suspend fun init() {
        producer = KafkaProducer(
            config.configuration(),
            Serdes.ByteArray().serializer(),
            serializer
        )
        publicationJobs = SupervisorJob()
    }
    override suspend fun stop() {
        publicationJobs.complete()
        publicationJobs.join()
        withContext(ioScope.coroutineContext) {
            tryAndLogOrNull(log) {
                producer.flush()
                producer.close(Duration.ofSeconds(10))
            }
        }
    }

    override suspend fun publish(meters: Collection<MeterSnapshot>) {
        ioScope.launch(publicationJobs) {
            val sentCount = producer.sendUntilFirstRejection(topic, meters) { exception ->
                exception?.let { log.error(it) { "A meter could not be sent to Kafka: ${it.message}" } }
            }
            if (sentCount < meters.size) {
                log.error { "${meters.size - sentCount} meters were not sent to Kafka, because the broker rejected them" }
            } else {
                log.debug { "${meters.size} meters were sent to Kafka" }
            }
        }
    }

    private companion object {

        val log = logger()
    }
}
