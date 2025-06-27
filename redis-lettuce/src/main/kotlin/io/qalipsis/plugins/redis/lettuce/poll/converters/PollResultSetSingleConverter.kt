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

package io.qalipsis.plugins.redis.lettuce.poll.converters

import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.plugins.redis.lettuce.RedisRecord
import io.qalipsis.plugins.redis.lettuce.poll.PollRawResult
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of [DatasourceObjectConverter], to convert the whole result set to several outputs containing one item each.
 *
 * @author Gabriel Moraes
 */
internal class PollResultSetSingleConverter(
    private val redisToJavaConverter: RedisToJavaConverter,
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: CampaignMeterRegistry?,
    redisMethod: String
) : DatasourceObjectConverter<PollRawResult<*>, RedisRecord<*>> {

    private lateinit var context: StepStartStopContext

    private val eventPrefix = "redis.lettuce.poll.$redisMethod"

    private val meterPrefix = "redis-lettuce-poll-$redisMethod"

    private var recordsCounter: Counter? = null

    private var recordsBytes: Counter? = null

    private lateinit var eventTags: Map<String, String>

    override fun start(context: StepStartStopContext) {
        meterRegistry?.apply {
            val tags = context.toMetersTags()
            val scenarioName = context.scenarioName
            val stepName = context.stepName
            recordsCounter = counter(scenarioName, stepName, "$meterPrefix-records", tags).report {
                display(
                    format = "attempted req: %,.0f",
                    severity = ReportMessageSeverity.INFO,
                    row = 0,
                    column = 0,
                    Counter::count
                )
            }
            recordsBytes = counter(scenarioName, stepName, "$meterPrefix-records-bytes", tags).report {
                display(
                    format = "received %,.0f bytes",
                    severity = ReportMessageSeverity.INFO,
                    row = 1,
                    column = 1,
                    Counter::count
                )
            }
        }
        this.context = context
        eventTags = context.toEventTags()
    }

    override fun stop(context: StepStartStopContext) {
        meterRegistry?.apply {
            recordsCounter = null
            recordsBytes = null
        }
    }

    /**
     * Converts the provided [value] from redis commands to a [RedisRecord] and send it item by item to the [output]
     * channel.
     *
     * This method uses [RedisToJavaConverter] to convert the specific redis return type to the specification for each command.
     * This method uses [RedisToJavaConverter] to get the size in bytes for each specific return type.
     *
     * When the [value] is provided by the ZSCAN, SSCAN or SCAN command it has a [List] type.
     * When the [value] is provided by the HSCAN command it has a [Map] type.
     *
     * @param offset to be sent in the [RedisRecord] offset.
     * @param value redis return specific type.
     * @param output channel to send the converted values in a flatten way.
     */
    override suspend fun supply(offset: AtomicLong, value: PollRawResult<*>, output: StepOutput<RedisRecord<*>>) {
        eventsLogger?.info("$eventPrefix.records-count", value.recordsCount, tags = eventTags)
        recordsCounter?.increment(value.recordsCount.toDouble())

        val records = when (value.records) {
            is List<*> -> value.records

            is Map<*, *> -> value.records.toList()

            else -> throw IllegalArgumentException("Not supported type: ${value::class}")
        }

        var bytes = 0
        records.forEach {
            val convertedItem = redisToJavaConverter.convert(it)
            bytes += redisToJavaConverter.getBytesCount(it)
            tryAndLogOrNull(log) {
                output.send(
                    RedisRecord(
                        recordOffset = offset.getAndIncrement(),
                        recordTimestamp = System.currentTimeMillis(),
                        value = convertedItem
                    )
                )
            }
        }
        eventsLogger?.info("$eventPrefix.records-bytes", bytes, tags = eventTags)
        recordsBytes?.increment(bytes.toDouble())
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
