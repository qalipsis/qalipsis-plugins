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

package io.qalipsis.plugins.redis.lettuce.configuration

import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.NotEmpty

/**
 * Connection for a single-connection operation using lettuce.
 *
 * @property nodes database nodes list, defaults to ["localhost:6379"].
 * @property database database number between 0 and 16, defaults to 0.
 * @property redisConnectionType defines the type of connection to use on redis commands, defaults to SINGLE.
 * @property authUser auth user to be used in ACL based connections, see [here](https://redis.io/commands/auth) for
 * more information.
 * @property authPassword auth password to be used in ACL based connections or in authenticated connection for redis
 * version < 6 or in password for redis SENTINEL connection type, see [here](https://redis.io/commands/auth) for more
 * information.
 * @property masterId redis sentinel master id, required in case of [redisConnectionType] SENTINEL.
 * See [here](https://lettuce.io/core/release/reference/#sentinel.redis-discovery-using-redis-sentinel) for more information.
 *
 * @author Gabriel Moraes
 */
data class RedisConnectionConfiguration internal constructor(
    @field:NotEmpty var nodes: List<String> = listOf("localhost:6379"),
    @field:Min(0) @field:Max(16) var database: Int = 0,
    var redisConnectionType: RedisConnectionType = RedisConnectionType.SINGLE,
    var authUser: String = "",
    var authPassword: String = "",
    var masterId: String = "",
)

/**
 * Supported redis connection types.
 *
 * @author Gabriel Moraes
 */
enum class RedisConnectionType {
    SINGLE, CLUSTER, SENTINEL
}