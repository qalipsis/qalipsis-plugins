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

package io.qalipsis.plugins.redis.lettuce.save.records

import io.qalipsis.plugins.redis.lettuce.save.LettuceSaveRecord
import io.qalipsis.plugins.redis.lettuce.save.RedisLettuceSaveMethod

/**
 * Qalipsis representation of a Redis record to save using HSET command.
 *
 * @property key of the record.
 * @property value of record.
 * @property redisMethod of the record to be saved.
 *
 * @author Gabriel Moraes
 */
data class HashRecord internal constructor(
    override val key: String,
    override val value: Map<String, String>,
    override var redisMethod: RedisLettuceSaveMethod
) : LettuceSaveRecord<Map<String, String>> {

    constructor(key: String, value: Map<String, String>): this(key, value, RedisLettuceSaveMethod.HSET)

    override fun getRecordBytesSize(): Int {
        return value.map { it.key.toByteArray().size + it.value.toByteArray().size }.sum() + key.toByteArray().size
    }
}
