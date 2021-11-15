package io.qalipsis.plugins.redis.lettuce.poll

import io.qalipsis.api.events.EventsLogger
import io.qalipsis.plugins.redis.lettuce.poll.scanners.LettuceHScanner
import io.qalipsis.plugins.redis.lettuce.poll.scanners.LettuceKeysScanner
import io.qalipsis.plugins.redis.lettuce.poll.scanners.LettuceSScanner
import io.qalipsis.plugins.redis.lettuce.poll.scanners.LettuceZScanner

/**
 * Factory to get a new implementation instance of [LettuceScanner].
 *
 * @author Gabriel Moraes
 */
internal object LettuceScanExecutorFactory {

    /**
     * Returns specific [LettuceScanner] for the provided type.
     *
     * @param type of the scan method.
     */
    @JvmStatic
    fun newInstance(type: RedisLettuceScanMethod, eventsLogger: EventsLogger?): LettuceScanner {
        return when (type) {
            RedisLettuceScanMethod.HSCAN -> LettuceHScanner(eventsLogger)
            RedisLettuceScanMethod.SCAN -> LettuceKeysScanner(eventsLogger)
            RedisLettuceScanMethod.SSCAN -> LettuceSScanner(eventsLogger)
            RedisLettuceScanMethod.ZSCAN -> LettuceZScanner(eventsLogger)
        }
    }
}
