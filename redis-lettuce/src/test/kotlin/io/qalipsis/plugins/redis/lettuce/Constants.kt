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

package io.qalipsis.plugins.redis.lettuce

import org.testcontainers.utility.DockerImageName

internal object Constants {
    const val REDIS_DOCKER_IMAGE = "redis:6.2.7"
    const val REDIS_5_DOCKER_IMAGE = "redis:5.0.14"
    const val REDIS_CLUSTER_DOCKER_IMAGE = "grokzen/redis-cluster:6.0.10"
    const val REDIS_7_DOCKER_IMAGE = "redis:7.0.4"

    @JvmStatic
    val REDIS_IMAGE_NAME: DockerImageName = DockerImageName.parse(REDIS_DOCKER_IMAGE)

    @JvmStatic
    val REDIS_CLUSTER_IMAGE_NAME: DockerImageName = DockerImageName.parse(REDIS_CLUSTER_DOCKER_IMAGE)
}
