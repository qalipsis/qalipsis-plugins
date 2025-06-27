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


/**
 * Qalipsis representation of a Redis record.
 *
 *
 * @property recordOffset offset of the processed record by Qalipsis.
 * @property recordTimestamp timestamp when the record was processed by Qalipsis.
 * @property value of the record after deserialization.
 *
 * @author Gabriel Moraes
 */
data class RedisRecord<V>(
    val recordOffset: Long,
    val recordTimestamp: Long,
    val value: V
)