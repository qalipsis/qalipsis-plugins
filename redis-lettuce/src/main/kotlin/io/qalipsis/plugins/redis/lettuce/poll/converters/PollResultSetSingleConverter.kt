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

package io.qalipsis.plugins.redis.lettuce.poll.converters

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
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
    private val meterRegistry: MeterRegistry?,
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
            recordsCounter = counter("$meterPrefix-records", tags)
            recordsBytes = counter("$meterPrefix-records-bytes", tags)
        }
        this.context = context
        eventTags = context.toEventTags();
    }

    override fun stop(context: StepStartStopContext) {
        meterRegistry?.apply {
            remove(recordsCounter!!)
            remove(recordsBytes!!)
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
            is List<*> -> {
                value.records
            }
            is Map<*, *> -> {
                value.records.toList()
            }
            else -> {
                throw IllegalArgumentException("Not supported type: ${value::class}")
            }
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
