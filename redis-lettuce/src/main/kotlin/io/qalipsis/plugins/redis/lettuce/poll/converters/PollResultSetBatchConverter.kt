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
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.plugins.redis.lettuce.RedisRecord
import io.qalipsis.plugins.redis.lettuce.poll.LettucePollMeters
import io.qalipsis.plugins.redis.lettuce.poll.LettucePollResult
import io.qalipsis.plugins.redis.lettuce.poll.PollRawResult
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of [DatasourceObjectConverter], to convert the whole result set into a unique record.
 *
 * @author Gabriel Moraes
 */
internal class PollResultSetBatchConverter(
    private val redisToJavaConverter: RedisToJavaConverter,
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: CampaignMeterRegistry?,
    redisMethod: String
) : DatasourceObjectConverter<PollRawResult<*>, LettucePollResult<Any?>> {

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
        eventTags = context.toEventTags()
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
     * Converts the provided [value] from redis commands to a [RedisRecord] and send it in a [LettucePollResult] to the
     * [output] channel.
     *
     * This method uses [RedisToJavaConverter] to convert the specific redis return type to the specification for each command.
     * This method uses [RedisToJavaConverter] to get the size in bytes for each specific return type.
     *
     * When the [value] is provided by the ZSCAN, SSCAN or SCAN command it has a [List] type.
     * When the [value] is provided by the HSCAN command it has a [Map] type.
     *
     * @param offset to be sent in the [RedisRecord] offset.
     * @param value redis return specific type.
     * @param output channel to send the converted values in batch.
     */
    override suspend fun supply(
        offset: AtomicLong,
        value: PollRawResult<*>,
        output: StepOutput<LettucePollResult<Any?>>
    ) {
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

        tryAndLogOrNull(log) {
            val bytes = AtomicInteger(0)
            output.send(
                LettucePollResult(
                    records = records.map {
                        bytes.addAndGet(redisToJavaConverter.getBytesCount(it))
                        RedisRecord(
                            recordOffset = offset.getAndIncrement(),
                            recordTimestamp = System.currentTimeMillis(),
                            value = redisToJavaConverter.convert(it)
                        )

                    },
                    meters = LettucePollMeters(
                        timeToResult = value.timeToResult,
                        pollCount = value.pollCount,
                        valuesBytesReceived = bytes.get(),
                        recordsCount = value.recordsCount
                    )
                )
            )
            eventsLogger?.info("$eventPrefix.records-bytes", bytes.toInt(), tags = eventTags)
            recordsBytes?.increment(bytes.toDouble())
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
