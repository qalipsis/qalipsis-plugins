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
