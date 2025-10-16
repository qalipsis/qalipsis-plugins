/*
 * Copyright 2024 AERIS IT Solutions GmbH
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

package io.qalipsis.plugins.influxdb.meters

import com.influxdb.client.InfluxDBClientOptions
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import com.influxdb.client.kotlin.WriteKotlinApi
import io.qalipsis.api.Executors
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.MeasurementPublisher
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.sync.SuspendedCountLatch
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Implementation of measurement publisher to export meters to influxdb.
 *
 * @author Francisca Eze
 */
class InfluxdbMeasurementPublisher(
    private val configuration: InfluxDbMeasurementConfiguration,
    @Named(Executors.BACKGROUND_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
) : MeasurementPublisher {

    private lateinit var client: InfluxDBClientKotlin

    private lateinit var writeClient: WriteKotlinApi

    private lateinit var publicationLatch: SuspendedCountLatch

    private lateinit var publicationSemaphore: Semaphore

    override suspend fun init() {
        publicationLatch = SuspendedCountLatch(0)
        publicationSemaphore = Semaphore(1)
        buildClient()
    }

    /**
     * Creates a brand-new client related to the configuration.
     *
     * @see InfluxDbMeasurementConfiguration
     */
    private fun buildClient() {
        client =
            InfluxDBClientKotlinFactory.create(
                InfluxDBClientOptions.builder()
                    .url(configuration.url)
                    .authenticate(
                        configuration.userName,
                        configuration.password.toCharArray()
                    )
                    .org(configuration.org)
                    .build()
            )
        writeClient = client.getWriteKotlinApi()
    }

    override suspend fun publish(meters: Collection<MeterSnapshot>) {
        publicationLatch.increment()
        coroutineScope.launch {
            try {
                publicationSemaphore.withPermit {
                    performPublish(meters)
                }
            } finally {
                publicationLatch.decrement()
            }
        }
    }

    private suspend fun performPublish(snapshots: Collection<MeterSnapshot>) {
        val records = snapshots.map(this::createRecord)
        logger.debug { "Exporting ${records.size} meters to InfluxDb" }
        try {
            saveRecords(records)
        } catch (ex: Exception) {
            logger.error(ex) { ex.message }
        }
    }

    /**
     * Generates a string record from a meter snapshot, structured in a format ideal for export to influxdb.
     */
    private fun createRecord(snapshot: MeterSnapshot): String {
        val meterId = snapshot.meterId
        val fields = snapshot.measurements.associate {
            if (it !is DistributionMeasurementMetric) {
                it.statistic.value to it.value
            } else {
                "${it.statistic.value}_${it.observationPoint}" to it.value
            }
        }
        val tags = if (meterId.tags.isNotEmpty()) {
            // Tags should be sanitized https://github.com/influxdata/influxdb/blob/master/tsdb/README.md
            meterId.tags
                .filterNot { (_, value) -> value.isNullOrBlank() }
                .mapValues { (_, value) -> value.replace(" ", "\\ ").replace(",", "\\,") }
                .entries.joinToString(",") + ",metric_type=${meterId.type.value}"
        } else "metric_type=${meterId.type.value}"
        return "${configuration.prefix}${meterId.meterName.format()},$tags ${fields.entries.joinToString(",")}"
    }


    private suspend fun saveRecords(records: Collection<String>) {
        try {
            writeClient.writeRecords(records, WritePrecision.MS, configuration.bucket, configuration.org)
            logger.debug { "Successfully sent ${records.size} metrics to InfluxDb" }
        } catch (ex: Exception) {
            logger.warn(ex) { "${records.size} could not be save: ${ex.message}" }
            throw ex
        }
    }

    override suspend fun stop() {
        logger.debug { "Stopping the meter publication of meters" }
        publicationLatch.await()
        logger.debug { "Closing the InfluxDbKotlin client" }
        tryAndLogOrNull(logger) {
            client.close()
        }
        logger.debug { "The meters publication logger was stopped" }
    }

    private fun String.format() = this.replace(" ", "-").lowercase()

    companion object {
        @JvmStatic
        private val logger = logger()
    }
}