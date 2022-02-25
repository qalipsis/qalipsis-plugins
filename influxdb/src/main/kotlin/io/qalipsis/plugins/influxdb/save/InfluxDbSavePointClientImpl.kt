package io.qalipsis.plugins.influxdb.save

import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.client.kotlin.WriteKotlinApi
import com.influxdb.client.write.Point
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.tryAndLog
import io.qalipsis.api.logging.LoggerHelper.logger
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Implementation of [InfluxDbSavePointClient].
 * Client to query to InfluxDB.
 *
 * @property clientBuilder supplier for the InfluxDb client.
 * @property eventsLogger the logger for events to track what happens during save step execution.
 * @property meterRegistry registry for the meters.
 *
 * @author Palina Bril
 */
internal class InfluxDbSavePointClientImpl(
    private val clientBuilder: () -> InfluxDBClientKotlin,
    private var eventsLogger: EventsLogger?,
    private val meterRegistry: MeterRegistry?
) : InfluxDbSavePointClient {

    private lateinit var client: InfluxDBClientKotlin

    private lateinit var writeApi: WriteKotlinApi

    private val eventPrefix = "influxdb.save"

    private val meterPrefix = "influxdb-save"

    private var pointsCounter: Counter? = null

    private var timeToResponse: Timer? = null

    private var successCounter: Counter? = null

    override suspend fun start(context: StepStartStopContext) {
        client = clientBuilder()
        writeApi = client.getWriteKotlinApi()
        meterRegistry?.apply {
            val tags = context.toMetersTags()
            pointsCounter = counter("$meterPrefix-saving-points", tags)
            timeToResponse = timer("$meterPrefix-time-to-response", tags)
            successCounter = counter("$meterPrefix-successes", tags)
        }
    }

    override suspend fun execute(
        bucketName: String,
        orgName: String,
        points: List<Point>,
        contextEventTags: Map<String, String>
    ): InfluxDbSaveQueryMeters {
        eventsLogger?.debug("$eventPrefix.saving-points", points.size, tags = contextEventTags)
        pointsCounter?.increment(points.size.toDouble())
        val requestStart = System.nanoTime()
        writeApi.writePoints(bucket = bucketName, org = orgName, points = points)
        val timeToResponseNano = System.nanoTime() - requestStart
        val timeToResponse = Duration.ofNanos(timeToResponseNano)
        eventsLogger?.info("${eventPrefix}.time-to-response", timeToResponse, tags = contextEventTags)
        eventsLogger?.info("${eventPrefix}.successes", points.size, tags = contextEventTags)
        successCounter?.increment(points.size.toDouble())
        this.timeToResponse?.record(timeToResponseNano, TimeUnit.NANOSECONDS)
        return InfluxDbSaveQueryMeters(
            timeToResult = timeToResponse,
            savedPoints = points.size
        )
    }

    override suspend fun stop(context: StepStartStopContext) {
        meterRegistry?.apply {
            remove(pointsCounter!!)
            remove(timeToResponse!!)
            remove(successCounter!!)
            pointsCounter = null
            timeToResponse = null
            successCounter = null
        }
        tryAndLog(log) {
            client.close()
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
