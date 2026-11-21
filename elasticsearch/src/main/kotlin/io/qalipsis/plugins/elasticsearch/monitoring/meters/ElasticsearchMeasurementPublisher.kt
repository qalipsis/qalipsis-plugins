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

package io.qalipsis.plugins.elasticsearch.monitoring.meters

import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.MeasurementPublisher
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.plugins.elasticsearch.monitoring.ElasticsearchOperationsImpl
import io.qalipsis.plugins.elasticsearch.monitoring.PublishingMode
import jakarta.inject.Named
import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.elasticsearch.client.Request

/**
 * Implementation of measurement publisher to export meters to elasticsearch.
 *
 * @author Francisca Eze
 */
@Requires(beans = [ElasticsearchMeasurementConfiguration::class])
internal class ElasticsearchMeasurementPublisher(
    @Named(Executors.BACKGROUND_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
    private val configuration: ElasticsearchMeasurementConfiguration,
) : MeasurementPublisher {

    private val indexFormatter = DateTimeFormatter.ofPattern(configuration.indexDatePattern)

    /**
     * Parent of the running publications, joined when stopping.
     * Tracking the jobs rather than counting them keeps the stop reliable, even when a job never starts or fails.
     */
    private var publicationJobs: CompletableJob = SupervisorJob()

    private var publicationSemaphore: Semaphore = Semaphore(configuration.publishers)

    @KTestable
    private val elasticsearchOperations = ElasticsearchOperationsImpl()

    /**
     * Initializes Elasticsearch and make it available for exporting of data.
     */
    override suspend fun init() {
        publicationJobs = SupervisorJob()
        publicationSemaphore = Semaphore(configuration.publishers)
        elasticsearchOperations.buildClient(configuration)
        elasticsearchOperations.initializeTemplate(configuration, PublishingMode.METERS)
    }

    override suspend fun publish(meters: Collection<MeterSnapshot>) {
        coroutineScope.launch(publicationJobs) {
            publicationSemaphore.withPermit {
                performPublish(meters)
            }
        }
    }

    private suspend fun performPublish(meterSnapshots: Collection<MeterSnapshot>) {
        logger.debug { "Sending ${meterSnapshots.size} meters to Elasticsearch" }
        // Convert the data for a bulk post.
        val serialized = meterSnapshots.mapNotNull(this@ElasticsearchMeasurementPublisher::metersToJsonConverter)
        if (serialized.isEmpty()) return
        val requestBody = serialized
            .joinToString(
                separator = "\n",
                postfix = "\n",
                transform = { elasticsearchOperations.createBulkItem(it, configuration.indexPrefix) }
            )
        val numberOfSentItems = serialized.size
        val bulkRequest = Request("POST", "_bulk")
        bulkRequest.setJsonEntity(requestBody)
        val exportStart = System.nanoTime()

        try {
            elasticsearchOperations.executeBulk(
                bulkRequest,
                exportStart,
                numberOfSentItems,
                null,
                coroutineScope,
                "meters"
            )
        } catch (e: Exception) {
            // TODO Reprocess 3 times when an exception is received.
            logger.error(e) { e.message }
        }
    }

    /**
     * Converts a collection of [MeterSnapshot]s to json format.
     *
     * Non-finite measurements (NaN / Infinity) are dropped because the Elasticsearch bulk API rejects
     * them as invalid JSON numbers. A snapshot with no finite measurements is skipped entirely.
     */
    private fun metersToJsonConverter(meterSnapshot: MeterSnapshot): Pair<String, String>? {
        val finiteMeasurements = meterSnapshot.measurements.filter { it.value.isFinite() }
        if (finiteMeasurements.isEmpty()) return null
        val stringBuilder = StringBuilder()
        val timestamp = indexFormatter.format(ZonedDateTime.ofInstant(meterSnapshot.timestamp, Clock.systemUTC().zone))
        val meterId = meterSnapshot.meterId
        val type = meterId.type.value.lowercase()
        val tags = meterId.tags
        stringBuilder.append("{\"")
            .append("@timestamp")
            .append("\":\"")
            .append(meterSnapshot.timestamp.toEpochMilli())
            .append('"')
            .append(",\"name\":\"")
            .append(meterId.meterName.format())
            .append('"')
            .append(",\"@type\":\"")
            .append(type)
            .append('"')
        if (tags.isNotEmpty()) {
            stringBuilder.append(",")
            val jsonTags = tags.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }
            stringBuilder.append(""""tags":{$jsonTags}""")
        }
        val measurementJson = finiteMeasurements.joinToString(separator = ",") {
            if (it is DistributionMeasurementMetric) {
                """{"${it.statistic.value.lowercase()}_${
                    it.observationPoint.toString()
                        .replace(".0", "") // Remove the trailing 0 from the integer percentages.
                        .replace(".", "_") // Remove the decimal separator from the percentages.
                }":${it.value}}"""
            } else {
                """{"${it.statistic.value.lowercase()}":${it.value}}"""
            }
        }

        stringBuilder.append(""","metrics":[$measurementJson]""")
        stringBuilder.append('}')

        return timestamp to stringBuilder.toString()
    }

    override suspend fun stop() {
        logger.debug { "Stopping the meter publication of meters" }
        publicationJobs.complete()
        publicationJobs.join()
        logger.debug { "Closing the Elasticsearch client" }
        tryAndLogOrNull(logger) {
            elasticsearchOperations.close()
        }
        logger.debug { "The meters logger was stopped" }
    }

    /**
     * Replace spaces to a hyphen(-) as well as convert to a lowercase. This is to provide uniformity among name indexes.
     */
    private fun String.format() = this.replace(" ", "-").lowercase()

    companion object {

        @JvmStatic
        private val logger = logger()
    }

}