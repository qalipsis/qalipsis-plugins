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

package io.qalipsis.plugins.redis.lettuce.poll

import io.lettuce.core.api.StatefulConnection
import kotlinx.coroutines.channels.Channel


/**
 * Specification to execute a specific scan command from Redis for each type of [RedisLettuceScanMethod].
 *
 * @author Gabriel Moraes
 */
internal interface LettuceScanner {

    /**
     * Returns the [RedisLettuceScanMethod] of the implementation.
     */
    fun getType(): RedisLettuceScanMethod

    /**
     * Executes a scan command based on the [RedisLettuceScanMethod] of the implementation.
     *
     * @param connection used to perform the command on Redis.
     * @param pattern used to perform the command.
     */
    suspend fun execute(
        connection: StatefulConnection<ByteArray, ByteArray>, pattern: String,
        resultsChannel: Channel<PollRawResult<*>>, contextEventTags: Map<String, String>
    )
}
