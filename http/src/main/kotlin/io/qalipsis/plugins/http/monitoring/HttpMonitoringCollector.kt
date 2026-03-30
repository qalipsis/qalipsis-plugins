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

package io.qalipsis.plugins.http.monitoring

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Throughput
import io.qalipsis.api.meters.Timer
import io.qalipsis.api.report.ReportMessageSeverity
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Collector of monitoring metrics and events for the HTTP Apache step.
 *
 * @author Eric Jessé
 */
internal class HttpMonitoringCollector(
    private val stepContext: StepContext<*, *>,
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: CampaignMeterRegistry?,
) {

    private val eventTags = stepContext.toEventTags()

    private val metersTags = stepContext.toMetersTags()

    private val scenarioName = stepContext.scenarioName

    private val stepName = stepContext.stepName

    private val connectingCounter by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        meterRegistry?.counter(scenarioName, stepName, "$METER_PREFIX-connecting", metersTags)?.report {
            display("conn.", ReportMessageSeverity.INFO) { 0 }
            display("\u27B6 %,.0f", ReportMessageSeverity.INFO, column = 1, toNumber = Counter::count)
        }
    }

    private val connectedTimer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        meterRegistry?.timer(scenarioName, stepName, "$METER_PREFIX-connected", metersTags)?.report {
            display(
                "\u2713 %,.0f",
                severity = ReportMessageSeverity.INFO,
                row = 0,
                column = 2,
                Timer::count
            )
            display(
                "mean: %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 0,
                column = 3
            ) { this.mean(TimeUnit.MILLISECONDS) }
            display(
                "max: %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 0,
                column = 4
            ) { this.max(TimeUnit.MILLISECONDS) }
        }
    }

    private val connectionFailureTimer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        meterRegistry?.timer(scenarioName, stepName, "$METER_PREFIX-connection-failure", metersTags)?.report {
            display(
                "\u2716 %,.0f",
                severity = ReportMessageSeverity.ERROR,
                row = 0,
                column = 5,
                Timer::count
            )
        }
    }

    private val tlsConnectedTimer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        meterRegistry?.timer(scenarioName, stepName, "$METER_PREFIX-tls-connected", metersTags)?.report {
            display("\nTLS", ReportMessageSeverity.INFO, row = 0) { 0 }
            display(
                "\n\u2713 %,.0f",
                severity = ReportMessageSeverity.INFO,
                row = 0,
                column = 2,
                Timer::count
            )
            display(
                "\n       %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 0,
                column = 3
            ) { this.mean(TimeUnit.MILLISECONDS) }
            display(
                "\n     %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 0,
                column = 4
            ) { this.max(TimeUnit.MILLISECONDS) }
        }
    }

    private val sentBytesCounter by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        meterRegistry?.counter(scenarioName, stepName, "$METER_PREFIX-sent-bytes", metersTags)?.report {
            display("\u2197 Req.", ReportMessageSeverity.INFO, row = 1) { 0 }
            display("\u2713 %,.0f bytes", ReportMessageSeverity.INFO, row = 1, column = 1, Counter::count)
        }
    }

    private val timeToFirstByteTimer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        meterRegistry?.timer(scenarioName, stepName, "$METER_PREFIX-time-to-first-byte", metersTags)?.report {
            display("\u2198 Resp.", ReportMessageSeverity.INFO, row = 2) { 0 }
            display("1st byte", ReportMessageSeverity.INFO, row = 2, column = 1) { 0 }
            display(
                "mean: %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 2,
                column = 2
            ) { this.mean(TimeUnit.MILLISECONDS) }
            display(
                "max: %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 2,
                column = 3
            ) { this.max(TimeUnit.MILLISECONDS) }
        }
    }

    private val receivedResponseTimer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        meterRegistry?.timer(scenarioName, stepName, "$METER_PREFIX-received-response", metersTags)?.report {
            display("\nlast byte", ReportMessageSeverity.INFO, row = 2, column = 1) { 0 }
            display(
                "\n      %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 2,
                column = 2
            ) { this.mean(TimeUnit.MILLISECONDS) }
            display(
                "\n     %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 2,
                column = 3
            ) { this.max(TimeUnit.MILLISECONDS) }
        }
    }

    private val receivedBytesCounter by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        meterRegistry?.counter(scenarioName, stepName, "$METER_PREFIX-received-bytes", metersTags)?.report {
            display("\u2713 %,.0f bytes", ReportMessageSeverity.INFO, row = 2, column = 4, Counter::count)
        }
    }

    private val requestFailureCounter by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        meterRegistry?.counter(scenarioName, stepName, "$METER_PREFIX-request-failure", metersTags)?.report {
            display("\u2716 %,.0f", ReportMessageSeverity.ERROR, row = 2, column = 5, Counter::count)
        }
    }

    private val requestThroughput by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        meterRegistry?.throughput(
            scenarioName, stepName, "$METER_PREFIX-throughput",
            unit = ChronoUnit.SECONDS, tags = metersTags
        )?.report {
            display("Throughput", ReportMessageSeverity.INFO, row = 4) { 0 }
            display(
                "cur: %,.1f req/s",
                severity = ReportMessageSeverity.INFO,
                row = 4,
                column = 1,
                Throughput::current
            )
            display(
                "mean: %,.1f req/s",
                severity = ReportMessageSeverity.INFO,
                row = 4,
                column = 2,
                Throughput::mean
            )
            display(
                "max: %,.1f req/s",
                severity = ReportMessageSeverity.INFO,
                row = 4,
                column = 3,
                Throughput::max
            )
        }
    }

    private val bytesThroughput by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        meterRegistry?.throughput(
            scenarioName, stepName, "$METER_PREFIX-throughput-bytes",
            unit = ChronoUnit.SECONDS, tags = metersTags
        )?.report {
            display(
                "\ncur: %,.0f B/s",
                severity = ReportMessageSeverity.INFO,
                row = 4,
                column = 1,
                Throughput::current
            )
            display(
                "\nmean: %,.0f B/s",
                severity = ReportMessageSeverity.INFO,
                row = 4,
                column = 2,
                Throughput::mean
            )
            display(
                "\nmax: %,.1f B/s",
                severity = ReportMessageSeverity.INFO,
                row = 4,
                column = 3,
                Throughput::max
            )
        }
    }

    fun recordConnecting() {
        eventsLogger?.info("$EVENT_PREFIX.connecting", tags = eventTags)
        connectingCounter?.increment()
    }

    fun recordConnected(duration: Duration) {
        eventsLogger?.info("$EVENT_PREFIX.connected", duration, tags = eventTags)
        connectedTimer?.record(duration)
    }

    fun recordConnectionFailure(duration: Duration, throwable: Throwable) {
        eventsLogger?.warn("$EVENT_PREFIX.connection-failure", arrayOf(duration, throwable), tags = eventTags)
        connectionFailureTimer?.record(duration)
    }

    fun recordTlsConnected(duration: Duration) {
        eventsLogger?.info("$EVENT_PREFIX.tls-connected", duration, tags = eventTags)
        tlsConnectedTimer?.record(duration)
    }

    fun recordSentBytes(bytesCount: Long) {
        if (bytesCount > 0) {
            eventsLogger?.debug("$EVENT_PREFIX.sent-bytes", bytesCount, tags = eventTags)
        }
        sentBytesCounter?.increment(bytesCount.toDouble())
    }

    fun recordTimeToFirstByte(duration: Duration) {
        eventsLogger?.debug("$EVENT_PREFIX.time-to-first-byte", duration, tags = eventTags)
        timeToFirstByteTimer?.record(duration)
    }

    fun recordReceivedResponse(duration: Duration, receivedBytes: Long) {
        eventsLogger?.info("$EVENT_PREFIX.received-response", arrayOf(duration, receivedBytes), tags = eventTags)
        receivedResponseTimer?.record(duration)
        requestThroughput?.record()
        bytesThroughput?.record(receivedBytes.toDouble())
    }

    fun recordReceivedBytes(bytesCount: Long) {
        if (bytesCount > 0) {
            eventsLogger?.debug("$EVENT_PREFIX.received-bytes", bytesCount, tags = eventTags)
        }
        receivedBytesCounter?.increment(bytesCount.toDouble())
    }

    fun recordRequestFailure(duration: Duration, throwable: Throwable) {
        eventsLogger?.warn("$EVENT_PREFIX.request-failure", arrayOf(duration, throwable), tags = eventTags)
        requestFailureCounter?.increment()
    }

    fun recordHttpStatus(statusCode: Int) {
        eventsLogger?.info("$EVENT_PREFIX.status", statusCode, tags = eventTags)
        meterRegistry?.counter(
            scenarioName,
            stepName,
            "$METER_PREFIX-status",
            metersTags + ("status" to statusCode.toString())
        )?.report {
            display("HTTP Status", ReportMessageSeverity.INFO, row = 3) { 0 }
            display(
                "$statusCode: %,.0f\n",
                ReportMessageSeverity.INFO,
                row = 3,
                column = 1,
                Counter::count
            )
        }?.increment()
    }

    companion object {

        const val EVENT_PREFIX = "http.apache"

        const val METER_PREFIX = "http-apache"
    }
}
