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
