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

import org.testcontainers.utility.DockerImageName

internal object Constants {
    const val REDIS_DOCKER_IMAGE = "redis:6.2.21"
    const val REDIS_CLUSTER_DOCKER_IMAGE = "grokzen/redis-cluster:7.2.5"
    const val REDIS_7_DOCKER_IMAGE = "redis:7.4.8"
    const val REDIS_8_DOCKER_IMAGE = "redis:8.6.1"

    @JvmStatic
    val REDIS_IMAGE_NAME: DockerImageName = DockerImageName.parse(REDIS_DOCKER_IMAGE)

    @JvmStatic
    val REDIS_CLUSTER_IMAGE_NAME: DockerImageName = DockerImageName.parse(REDIS_CLUSTER_DOCKER_IMAGE)
}
