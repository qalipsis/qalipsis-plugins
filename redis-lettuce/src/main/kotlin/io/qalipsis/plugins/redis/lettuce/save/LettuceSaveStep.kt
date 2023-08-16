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

package io.qalipsis.plugins.redis.lettuce.save

import io.lettuce.core.RedisFuture
import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.tryAndLog
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.api.sync.asSuspended
import io.qalipsis.plugins.redis.lettuce.LettuceMonitoringCollector
import io.qalipsis.plugins.redis.lettuce.configuration.RedisCommandsFactory
import io.qalipsis.plugins.redis.lettuce.save.records.HashRecord
import io.qalipsis.plugins.redis.lettuce.save.records.SetRecord
import io.qalipsis.plugins.redis.lettuce.save.records.SortedRecord
import io.qalipsis.plugins.redis.lettuce.save.records.ValueRecord
import kotlinx.coroutines.withContext
import java.time.Duration
import kotlin.coroutines.CoroutineContext

/**
 * Implementation of a [io.qalipsis.api.steps.Step] able to save a batch of records into a Redis database.
 *
 * @property id of the step.
 * @property retryPolicy of the step.
 * @property connectionFactory supplier to create the connection with the Redis database.
 * @property recordsFactory closure to generate the records to be saved.
 * @property meterRegistry registry for the meters.
 * @property eventsLogger logger for the events.
 *
 * @author Gabriel Moraes
 */
internal class LettuceSaveStep<I>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    private val ioCoroutineContext: CoroutineContext,
    private val connectionFactory: suspend () -> StatefulConnection<ByteArray, ByteArray>,
    private val recordsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<LettuceSaveRecord<*>>,
    private val meterRegistry: CampaignMeterRegistry?,
    private val eventsLogger: EventsLogger?,
) : AbstractStep<I, LettuceSaveResult<I>>(id, retryPolicy) {

    private lateinit var connection: StatefulConnection<ByteArray, ByteArray>

    private lateinit var redisAsyncCommands: RedisClusterAsyncCommands<ByteArray, ByteArray>

    private lateinit var monitoringCollector: LettuceMonitoringCollector

    private var sendingBytes: Counter? = null

    private var sentBytesMeter: Counter? = null

    private var sendingFailure: Counter? = null

    private val meterPrefix = "redis-lettuce-save"

    override suspend fun start(context: StepStartStopContext) {
        meterRegistry?.apply {
            val tags = context.toEventTags()
            val scenarioName = context.scenarioName
            val stepName = context.stepName
            sendingBytes = counter(scenarioName, stepName,"$meterPrefix-sending-bytes", tags).report {
                display(
                    format = "attempted saves: %,.0f bytes",
                    severity = ReportMessageSeverity.INFO,
                    row = 0,
                    column = 1,
                    Counter::count
                )
            }
            sentBytesMeter = counter(scenarioName, stepName, "$meterPrefix-sent-bytes", tags).report {
                display(
                    format = "\u2713 %,.0f byte successes",
                    severity = ReportMessageSeverity.INFO,
                    row = 0,
                    column = 3,
                    Counter::count
                )
            }
            sendingFailure = counter(scenarioName, stepName, "$meterPrefix-sending-failure", tags).report {
                display(
                    format = "\u2716 %,.0f failures",
                    severity = ReportMessageSeverity.ERROR,
                    row = 0,
                    column = 4,
                    Counter::count
                )
            }
        }
        connection = connectionFactory()
        redisAsyncCommands = RedisCommandsFactory.getAsyncCommand(connection)
    }

    override suspend fun execute(context: StepContext<I, LettuceSaveResult<I>>) {
        val input = context.receive()

        val records = recordsFactory(context, input)

        monitoringCollector =
            LettuceMonitoringCollector(context, eventsLogger, sendingBytes, sentBytesMeter, sendingFailure, "save")

        context.send(execute(monitoringCollector, input, records))
    }

    private suspend fun execute(
        monitoringCollector: LettuceMonitoringCollector,
        input: I,
        records: List<LettuceSaveRecord<*>>
    ): LettuceSaveResult<I> {
        records.forEach { record ->
            withContext(ioCoroutineContext) {
                save(record, monitoringCollector)
            }
        }
        return monitoringCollector.toSaveResult(input)
    }

    private suspend fun save(lettuceSaveRecord: LettuceSaveRecord<*>, monitoringCollector: LettuceMonitoringCollector) {
        val sentStart = System.nanoTime()
        val recordsByteSize = lettuceSaveRecord.getRecordBytesSize()
        monitoringCollector.recordSendingData(recordsByteSize)

        try {
            when (lettuceSaveRecord) {
                is HashRecord -> saveHashRecord(lettuceSaveRecord)
                is SortedRecord -> saveSortedRecord(lettuceSaveRecord)
                is SetRecord -> saveSetRecord(lettuceSaveRecord)
                is ValueRecord -> saveValueRecord(lettuceSaveRecord)
                else -> throw IllegalStateException("Not supported Lettuce save record type")
            }.asSuspended().get(DEFAULT_TIMEOUT)

            val now = System.nanoTime()
            monitoringCollector.recordSentDataSuccess(Duration.ofNanos(now - sentStart), recordsByteSize)
        } catch (e: Exception) {

            val now = System.nanoTime()
            monitoringCollector.recordSentDataFailure(Duration.ofNanos(now - sentStart), e)
        }
    }

    private fun saveValueRecord(lettuceSaveRecord: ValueRecord): RedisFuture<out Any> {
        return redisAsyncCommands.set(lettuceSaveRecord.key.toByteArray(), lettuceSaveRecord.value.toByteArray())
    }

    private fun saveSetRecord(lettuceSaveRecord: SetRecord): RedisFuture<out Any> {
        return redisAsyncCommands.sadd(lettuceSaveRecord.key.toByteArray(), lettuceSaveRecord.value.toByteArray())
    }

    private fun saveSortedRecord(lettuceSaveRecord: SortedRecord): RedisFuture<out Any> {
        return redisAsyncCommands.zadd(
            lettuceSaveRecord.key.toByteArray(),
            lettuceSaveRecord.value.first,
            lettuceSaveRecord.value.second.toByteArray()
        )
    }

    private fun saveHashRecord(lettuceSaveRecord: HashRecord): RedisFuture<out Any> {
        val byteArrayMap = lettuceSaveRecord.value.map { it.key.toByteArray() to it.value.toByteArray() }.toMap()
        return redisAsyncCommands.hset(lettuceSaveRecord.key.toByteArray(), byteArrayMap)
    }

    override suspend fun stop(context: StepStartStopContext) {
        meterRegistry?.apply {
            sendingBytes = null
            sentBytesMeter = null
            sendingFailure = null
        }
        tryAndLog(log) { connection.closeAsync()?.asSuspended() }
        tryAndLog(log) { connection.resources.shutdown() }
    }

    companion object {

        private val DEFAULT_TIMEOUT = Duration.ofSeconds(10)

        @JvmStatic
        private val log = logger()
    }
}
