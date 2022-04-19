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

package io.qalipsis.plugins.graphite.save

import io.netty.channel.EventLoopGroup
import io.qalipsis.api.annotations.Spec
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

/**
 * Interface to establish a connection with Graphite
 */
@Spec
interface GraphiteSaveConnectionSpecification {
    /**
     * Configures the servers settings.
     */
    fun server(host: String, port: Int)

    /**
     * Configures the basic connection.
     */
    fun workerGroup(workerGroupConfigurer: () -> EventLoopGroup)
}

@Spec
internal class GraphiteSaveConnectionSpecificationImpl : GraphiteSaveConnectionSpecification {

    @field:NotBlank
    var host: String = "localhost"

    @field:NotNull
    var port: Int = 8080

    lateinit var workerGroup: () -> EventLoopGroup

    override fun server(host: String, port: Int) {
        this.host = host
        this.port = port
    }

    override fun workerGroup(workerGroupConfigurer: () -> EventLoopGroup) {
        this.workerGroup = workerGroupConfigurer
    }
}