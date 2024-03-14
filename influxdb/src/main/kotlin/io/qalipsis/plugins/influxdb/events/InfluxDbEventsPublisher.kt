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

package io.qalipsis.plugins.influxdb.events

import com.influxdb.client.InfluxDBClientOptions
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import com.influxdb.client.kotlin.WriteKotlinApi
import com.influxdb.client.write.Point
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.events.AbstractBufferedEventsPublisher
import io.qalipsis.api.events.Event
import io.qalipsis.api.events.EventGeoPoint
import io.qalipsis.api.events.EventRange
import io.qalipsis.api.lang.durationSinceNanos
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.sync.SuspendedCountLatch
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Duration
import java.time.temporal.Temporal
import kotlin.coroutines.CoroutineContext

/**
 * Implementation of [io.qalipsis.api.events.EventsPublisher] for InfluxDb.
 *
 * */
@Singleton
@Requires(beans = [InfluxDbEventsConfiguration::class])
internal class InfluxDbEventsPublisher(
    @Named(Executors.BACKGROUND_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
    @Named(Executors.BACKGROUND_EXECUTOR_NAME) private val coroutineContext: CoroutineContext,
    private val configuration: InfluxDbEventsConfiguration,
    private val meterRegistry: CampaignMeterRegistry,
) : AbstractBufferedEventsPublisher(
    configuration.minLevel,
    configuration.lingerPeriod,
    configuration.batchSize,
    coroutineScope
) {

    lateinit var client: InfluxDBClientKotlin

    private lateinit var publicationLatch: SuspendedCountLatch

    private lateinit var publicationSemaphore: Semaphore

    lateinit var writeClient: WriteKotlinApi

    override fun start() {
        publicationLatch = SuspendedCountLatch(0)
        publicationSemaphore = Semaphore(configuration.publishers)
        super.start()
        buildClient(configuration.url)
    }

    /**
     * Creates a brand-new client related to the configuration.
     *
     * @see InfluxDbEventsConfiguration
     */
    private fun buildClient(configurationUrl: String) {
        client =
            InfluxDBClientKotlinFactory.create(
                InfluxDBClientOptions.builder()
                    .url(configurationUrl)
                    .authenticate(
                        configuration.username,
                        configuration.password.toCharArray()
                    )
                    .org(configuration.org)
                    .build()
            )
        writeClient = client.getWriteKotlinApi()
    }

    override fun stop() {
        log.debug { "Stopping the events logger with ${buffer.size} events in the buffer" }
        super.stop()
        runBlocking(coroutineContext) {
            log.debug { "Waiting for ${publicationLatch.get()} publication jobs to be completed" }
            publicationLatch.await()
        }
        log.debug { "Closing the InfluxDbKotlin client" }
        tryAndLogOrNull(log) {
            client.close()
        }
        log.debug { "The events logger was stopped" }
    }

    override suspend fun publish(values: List<Event>) {
        publicationLatch.increment()
        coroutineScope.launch {
            try {
                publicationSemaphore.withPermit {
                    performPublish(values)
                }
            } finally {
                publicationLatch.decrement()
            }
        }
    }

    private suspend fun performPublish(values: List<Event>) {
        log.debug { "Sending ${values.size} events to InfluxDb" }
        val conversionStart = System.nanoTime()
        val points = values
            .map(this@InfluxDbEventsPublisher::createPoint)
            .toList()

        meterRegistry.timer(
            scenarioName = "",
            stepName = "",
            name = EVENTS_CONVERSIONS_TIMER_NAME,
            tags = mapOf("publisher" to "influxdb")
        )
            .record(conversionStart.durationSinceNanos())
        val numberOfSentItems = values.size
        meterRegistry.counter(
            scenarioName = "",
            stepName = "",
            name = EVENTS_COUNT_TIMER_NAME,
            tags = mapOf("publisher" to "influxdb")
        )
            .increment(numberOfSentItems.toDouble())

        val exportStart = System.nanoTime()
        try {
            savePoints(points, exportStart)
        } catch (e: Exception) {
            meterRegistry.timer(
                scenarioName = "",
                stepName = "",
                name = EVENTS_EXPORT_TIMER_NAME,
                tags = mapOf("publisher" to "influxdb", "status" to "error")
            )
                .record(exportStart.durationSinceNanos())
            log.error(e) { e.message }
        }
    }

    private suspend fun savePoints(points: List<Point>, exportStart: Long) {
        try {
            writeClient.writePoints(points, configuration.bucket, configuration.org)
            val exportEnd = System.nanoTime()
            meterRegistry.timer(
                scenarioName = "",
                stepName = "",
                name = EVENTS_EXPORT_TIMER_NAME,
                tags = mapOf("publisher" to "influxdb", "status" to "success")
            )
                .record(Duration.ofNanos(exportEnd - exportStart))
            log.debug { "Successfully sent ${points.size} events to InfluxDb" }
            log.trace { "onSuccess totally processed" }
        } catch (e: Exception) {
            log.warn(e) { "${points.size} could not be save: ${e.message}" }
            throw e
        }
    }

    /**
     * Creates a points from raw data.
     **/
    private fun createPoint(event: Event): Point {
        val point = Point(event.name)
            .time(event.timestamp.toEpochMilli() * 1000000, WritePrecision.NS)
            .addField("level", "${event.level}".lowercase())
        if (event.value != null) {
            when (event.value) {
                is Collection<*> -> {
                    (event.value as Collection<*>).forEach { defineFieldName(point, it) }
                }

                is Array<*> -> {
                    (event.value as Array<*>).forEach { defineFieldName(point, it) }
                }

                else -> {
                    defineFieldName(point, event.value)
                }
            }
        }
        event.tags.forEach { tag -> point.addTag(tag.key, tag.value) }
        return point
    }

    private fun defineFieldName(point: Point, eventValue: Any?) {
        when (eventValue) {
            is String -> point.addField("message", eventValue)
            is Boolean -> point.addField("boolean", "$eventValue")
            is Number -> point.addField("number", "$eventValue")
            is Temporal -> point.addField("date", "$eventValue")
            is Throwable -> point.addField("error", "${eventValue.message}")
            is Duration -> point.addField("duration", "$eventValue")
            is EventGeoPoint -> point.addField("latitude", "${eventValue.latitude}")
                .addField("longitude", "${eventValue.longitude}")

            is EventRange<*> -> {
                val leftBorder: String = if (eventValue.includeLower) "[" else "("
                val rightBorder: String = if (eventValue.includeUpper) "]" else ")"
                point.addField(
                    "range",
                    "$leftBorder${eventValue.lowerBound} : ${eventValue.upperBound}$rightBorder"
                )
            }

            else -> point.addField("other", "$eventValue")
        }
    }

    companion object {

        private const val EVENTS_CONVERSIONS_TIMER_NAME = "influxdb.events.conversion"

        private const val EVENTS_COUNT_TIMER_NAME = "influxdb.events.converted"

        private const val EVENTS_EXPORT_TIMER_NAME = "influxdb.events.export"

        @JvmStatic
        private val log = logger()
    }
}
