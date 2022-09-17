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

package io.qalipsis.plugins.redis.lettuce.streams.consumer

/**
 * Qalipsis representation of a consumed Redis Streams record.
 *
 * @property offset of the record consumed by Qalipsis.
 * @property consumedTimestamp timestamp when the message was consumed by Qalipsis.
 * @property streamKey from where the message was consumed.
 * @property id of the message.
 * @property value of the record.
 *
 * @author Gabriel Moraes
 */
data class LettuceStreamsConsumedRecord(
    val offset: Long,
    val consumedTimestamp: Long,
    val id: String,
    val streamKey: String,
    val value: Map<String, String>,
) {
    internal constructor(offset: Long, id: String, stream: ByteArray, value: Map<String, String>) : this(
        offset = offset,
        consumedTimestamp = System.currentTimeMillis(),
        id = id,
        streamKey = stream.decodeToString(),
        value = value
    )
}
