package io.qalipsis.plugins.influxdb.poll

import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.query.FluxRecord
import io.aerisconsulting.catadioptre.KTestable
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.concurrentList
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.datasource.DatasourceIterativeReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Duration

/**
 * Database reader
 *
 * @property pollStatement statement to execute
 * @property pollDelay duration between the end of a poll and the start of the next one
 * @property resultsChannelFactory factory to create the channel containing the received results sets
 * @property running running state of the reader
 * @property pollingJob instance of the background job polling data from the database
 * @property eventsLogger the logger for events to track what happens during save query execution.
 * @property meterRegistry registry for the meters.
 *
 * @author Alex Averyanov
 */
internal class InfluxDbIterativeReader(
    private val coroutineScope: CoroutineScope,
    private val clientFactory: () -> InfluxDBClientKotlin,
    private val pollStatement: PollStatement,
    private val pollDelay: Duration,
    private val resultsChannelFactory: () -> Channel<InfluxDbQueryResult> = { Channel(Channel.UNLIMITED) },
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: MeterRegistry?
) : DatasourceIterativeReader<InfluxDbQueryResult> {

    private val eventPrefix = "influxdb.poll"

    private val meterPrefix = "influxdb-poll"

    private var running = false

    private lateinit var client: InfluxDBClientKotlin

    private lateinit var pollingJob: Job

    private lateinit var resultsChannel: Channel<InfluxDbQueryResult>

    private lateinit var context: StepStartStopContext

    private var recordsCount: Counter? = null

    private var timeToResponse: Timer? = null

    private var failureCounter: Counter? = null

    override fun start(context: StepStartStopContext) {
        log.debug { "Starting the step with the context $context" }
        meterRegistry?.apply {
            val tags = context.toMetersTags()
            recordsCount = counter("$meterPrefix-received-records", tags)
            timeToResponse = timer("$meterPrefix-time-to-response", tags)
            failureCounter = counter("$meterPrefix-failures", tags)
        }
        this.context = context
        init()
        pollingJob = coroutineScope.launch {
            log.debug { "Polling job just started for context $context" }
            try {
                while (running) {
                    poll(client)
                    if (running) {
                        delay(pollDelay.toMillis())
                    }
                }
                log.debug { "Polling job just completed for context $context" }
            } finally {
                resultsChannel.cancel()
            }
        }
        running = true
    }

    override fun stop(context: StepStartStopContext) {
        meterRegistry?.apply {
            remove(recordsCount!!)
            remove(timeToResponse!!)
            remove(failureCounter!!)
            recordsCount = null
            timeToResponse = null
            failureCounter = null
        }
        running = false
        runCatching {
            runBlocking {
                pollingJob.cancelAndJoin()
            }
        }
        runCatching {
            client.close()
        }
        resultsChannel.cancel()
        pollStatement.reset()
    }

    @KTestable
    fun init() {
        resultsChannel = resultsChannelFactory()
        client = clientFactory()
    }

    private suspend fun poll(client: InfluxDBClientKotlin) {
        val records = concurrentList<FluxRecord>()
        eventsLogger?.trace("$eventPrefix.polling", tags = context.toEventTags())
        val requestStart = System.nanoTime()
        try {
            val results = client.getQueryKotlinApi().query(pollStatement.getNextQuery())
            val timeToSuccess = Duration.ofNanos(System.nanoTime() - requestStart)
            for (record in results) {
                // FIXME The values are not really sorted.
                records.add(record)
            }
            recordsCount?.increment(records.size.toDouble())
            eventsLogger?.info(
                "$eventPrefix.successful-response",
                arrayOf(timeToSuccess, records.size),
                tags = context.toEventTags()
            )
            pollStatement.saveTiebreaker(records)
            resultsChannel.send(
                InfluxDbQueryResult(
                    results = records,
                    meters = InfluxDbQueryMeters(records.size, timeToSuccess)
                )
            )
        } catch (e: InterruptedException) {
            // The exception is ignored.
        } catch (e: CancellationException) {
            // The exception is ignored.
        } catch (e: Exception) {
            val timeToFailure = Duration.ofNanos(System.nanoTime() - requestStart)
            failureCounter?.increment()
            eventsLogger?.warn(
                "$eventPrefix.failure",
                arrayOf(e, timeToFailure),
                tags = context.toEventTags()
            )
            log.debug(e) { e.message }
        }
    }

    override suspend fun hasNext(): Boolean = running

    override suspend fun next(): InfluxDbQueryResult = resultsChannel.receive()

    private companion object {
        val log = logger()
    }
}