package io.qalipsis.plugins.graphite

import io.netty.channel.EventLoopGroup
import io.qalipsis.api.annotations.Spec
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

/**
 * Interface to establish a connection with Graphite
 */
interface GraphiteConnectionSpecification {
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
internal class GraphiteConnectionSpecificationImpl : GraphiteConnectionSpecification {

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