package io.qalipsis.plugins.redis.lettuce

import java.time.Duration

internal interface MonitoringCollector {

    fun recordSendingData(bytesToBeSent: Int)

    fun recordSentDataSuccess(timeToSent: Duration, sentBytes: Int)

    fun recordSentDataFailure(timeToFailure: Duration, throwable: Throwable)

}
