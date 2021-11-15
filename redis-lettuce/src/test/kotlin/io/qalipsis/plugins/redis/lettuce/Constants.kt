package io.qalipsis.plugins.redis.lettuce

import org.testcontainers.utility.DockerImageName

internal object Constants {
    const val REDIS_DOCKER_IMAGE = "redis:6.0.8"
    const val REDIS_5_DOCKER_IMAGE = "redis:5.0.10"
    const val REDIS_CLUSTER_DOCKER_IMAGE = "grokzen/redis-cluster:6.0.10"

    @JvmStatic
    val REDIS_IMAGE_NAME: DockerImageName = DockerImageName.parse(REDIS_DOCKER_IMAGE)

    @JvmStatic
    val REDIS_CLUSTER_IMAGE_NAME: DockerImageName = DockerImageName.parse(REDIS_CLUSTER_DOCKER_IMAGE)
}
