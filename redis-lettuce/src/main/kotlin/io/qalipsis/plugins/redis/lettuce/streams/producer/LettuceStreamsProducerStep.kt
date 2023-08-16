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

package io.qalipsis.plugins.redis.lettuce.streams.producer

import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
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
import kotlinx.coroutines.withContext
import java.time.Duration
import kotlin.coroutines.CoroutineContext

/**
 * Implementation of a [io.qalipsis.api.steps.Step] able to produce a message into a Redis streams.
 *
 * @property id id of the step.
 * @property retryPolicy of the step.
 * @property connectionFactory supplier to create the connection with the Redis broker.
 * @property recordsFactory closure to generate the records to be published.
 * @property meterRegistry registry for the meters.
 * @property eventsLogger logger for the events.
 *
 * @author Gabriel Moraes
 */
internal class LettuceStreamsProducerStep<I>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    private val ioCoroutineContext: CoroutineContext,
    private val connectionFactory: suspend () -> StatefulConnection<ByteArray, ByteArray>,
    private val recordsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<LettuceStreamsProduceRecord>,
    private val meterRegistry: CampaignMeterRegistry?,
    private val eventsLogger: EventsLogger?,
) : AbstractStep<I, LettuceStreamsProducerResult<I>>(id, retryPolicy) {

    private lateinit var connection: StatefulConnection<ByteArray, ByteArray>

    private lateinit var asyncCommand: RedisClusterAsyncCommands<ByteArray, ByteArray>

    private lateinit var monitoringCollector: LettuceMonitoringCollector

    private var sendingBytes: Counter? = null

    private var sentBytesMeter: Counter? = null

    private var sendingFailure: Counter? = null

    private val meterPrefix = "redis-lettuce-streams"

    override suspend fun start(context: StepStartStopContext) {
        meterRegistry?.apply {
            val tags = context.toEventTags()
            val scenarioName = context.scenarioName
            val stepName = context.stepName
            sendingBytes = counter(scenarioName, stepName, "$meterPrefix-sending-bytes", tags).report {
                display(
                    format = "attempted req: %,.0f bytes",
                    severity = ReportMessageSeverity.INFO,
                    row = 0,
                    column = 0,
                    Counter::count
                )
            }
            sentBytesMeter = counter(scenarioName, stepName, "$meterPrefix-sent-bytes", tags).report {
                display(
                    format = "\u2713 %,.0f successes",
                    severity = ReportMessageSeverity.INFO,
                    row = 0,
                    column = 2,
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
        initialize()
    }

    private fun initialize() {
        asyncCommand = when (connection) {
            is StatefulRedisClusterConnection ->
                (connection as StatefulRedisClusterConnection<ByteArray, ByteArray>).async()
            is StatefulRedisConnection ->
                (connection as StatefulRedisConnection<ByteArray, ByteArray>).async()
            else -> throw IllegalStateException("Connection type is not implemented to perform redis commands")
        }
    }

    override suspend fun execute(context: StepContext<I, LettuceStreamsProducerResult<I>>) {
        val input = context.receive()
        val records = recordsFactory(context, input)

        monitoringCollector =
            LettuceMonitoringCollector(context, eventsLogger, sendingBytes, sentBytesMeter, sendingFailure, "streams")
        context.send(execute(monitoringCollector, input, records))
    }

    private suspend fun execute(
        monitoringCollector: LettuceMonitoringCollector,
        input: I,
        records: List<LettuceStreamsProduceRecord>
    ): LettuceStreamsProducerResult<I> {
        records.forEach { record ->
            withContext(ioCoroutineContext) {
                produce(record, monitoringCollector)
            }
        }

        return monitoringCollector.toResult(input)
    }

    private suspend fun produce(
        lettuceStreamsProduceRecord: LettuceStreamsProduceRecord,
        monitoringCollector: LettuceMonitoringCollector
    ) {
        val sentStart = System.nanoTime()
        val recordsByteSize =
            lettuceStreamsProduceRecord.value.map { it.key.toByteArray().size + it.value.toByteArray().size }.sum()
        monitoringCollector.recordSendingData(recordsByteSize)

        try {
            asyncCommand.xadd(
                lettuceStreamsProduceRecord.key.toByteArray(),
                lettuceStreamsProduceRecord.value.map { it.key.toByteArray() to it.value.toByteArray() }.toMap()
            ).asSuspended().get(DEFAULT_TIMEOUT)

            val now = System.nanoTime()
            monitoringCollector.recordSentDataSuccess(Duration.ofNanos(now - sentStart), recordsByteSize)
        } catch (e: Exception) {

            val now = System.nanoTime()
            monitoringCollector.recordSentDataFailure(Duration.ofNanos(now - sentStart), e)
        }
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
