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

package io.qalipsis.plugins.redis.lettuce

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.Counter
import io.qalipsis.plugins.redis.lettuce.save.LettuceSaveResult
import io.qalipsis.plugins.redis.lettuce.streams.producer.LettuceStreamsProducerResult
import java.time.Duration

internal class LettuceMonitoringCollector(
    private val stepContext: StepContext<*, *>,
    private val eventsLogger: EventsLogger?,
    private var sendingBytes: Counter?,
    private var sentBytesMeter: Counter?,
    private var sendingFailure: Counter?,
    stepQualifier: String
) : MonitoringCollector {
    private var sendingFailures: MutableList<Throwable>? = mutableListOf()

    private val meters = MetersImpl()

    private val eventPrefix = "redis.lettuce.${stepQualifier}"

    override fun recordSendingData(bytesToBeSent: Int) {
        meters.bytesToBeSent += bytesToBeSent
        eventsLogger?.info("${eventPrefix}.sending.bytes", bytesToBeSent, tags = stepContext.toEventTags())
        sendingBytes?.increment(bytesToBeSent.toDouble())
    }

    override fun recordSentDataSuccess(timeToSent: Duration, sentBytes: Int) {
        meters.sentBytes += sentBytes
        eventsLogger?.info(
            "${eventPrefix}.sent.bytes",
            arrayOf(timeToSent, sentBytes),
            tags = stepContext.toEventTags()
        )
        sentBytesMeter?.increment(sentBytes.toDouble())
    }

    override fun recordSentDataFailure(timeToFailure: Duration, throwable: Throwable) {
        sendingFailures?.add(throwable)
        eventsLogger?.warn(
            "${eventPrefix}.sending.failed",
            arrayOf(timeToFailure, throwable),
            tags = stepContext.toEventTags()
        )
        sendingFailure?.increment()

        stepContext.addError(StepError(throwable))
    }

    fun <IN> toResult(input: IN) = LettuceStreamsProducerResult(
        input,
        sendingFailures,
        meters
    )

    fun <IN> toSaveResult(input: IN) = LettuceSaveResult(
        input,
        sendingFailures,
        meters
    )
}
