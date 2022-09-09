package io.qalipsis.plugins.influxdb.save

import com.influxdb.client.InfluxDBClientOptions
import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import com.influxdb.client.write.Point
import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.influxdb.InfluxDbStepConnectionImpl
import io.qalipsis.plugins.influxdb.InfluxdbStepSpecification

/**
 * Specification for a [io.qalipsis.plugins.influxdb.save.InfluxDbSaveStep] to save data to a InfluxDB.
 *
 * @author Palina Bril
 */
interface InfluxDbSaveStepSpecification<I> :
    StepSpecification<I, I, InfluxDbSaveStepSpecification<I>>,
    ConfigurableStepSpecification<I, I, InfluxDbSaveStepSpecification<I>>,
    InfluxdbStepSpecification<I, I, InfluxDbSaveStepSpecification<I>> {

    /**
     * Configures the connection to the InfluxDb server.
     */
    fun connect(connectionConfiguration: InfluxDbStepConnectionImpl.() -> Unit)

    /**
     * Defines the statement to execute when saving.
     */
    fun query(queryConfiguration: InfluxDbSavePointConfiguration<I>.() -> Unit)

    /**
     * Configures the monitoring of the save step.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)

}

/**
 * Implementation of [InfluxDbSaveStepSpecification].
 *
 */
@Spec
internal class InfluxDbSaveStepSpecificationImpl<I> :
    InfluxDbSaveStepSpecification<I>,
    AbstractStepSpecification<I, I, InfluxDbSaveStepSpecification<I>>() {

    internal var connectionConfig = InfluxDbStepConnectionImpl()

    internal lateinit var clientBuilder: (() -> InfluxDBClientKotlin)

    internal var queryConfiguration = InfluxDbSavePointConfiguration<I>()

    internal var monitoringConfig = StepMonitoringConfiguration()

    override fun connect(connectionConfiguration: InfluxDbStepConnectionImpl.() -> Unit) {
        connectionConfig.connectionConfiguration();
        clientBuilder = {
            InfluxDBClientKotlinFactory.create(
                InfluxDBClientOptions.builder()
                    .url(connectionConfig.url)
                    .authenticate(
                        connectionConfig.user,
                        connectionConfig.password.toCharArray()
                    )
                    .org(connectionConfig.org)
                    .build()
            )
        }
    }

    override fun query(queryConfig: InfluxDbSavePointConfiguration<I>.() -> Unit) {
        queryConfiguration.queryConfig()
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }
}

/**
 * Configuration of routing and generation of points to save in InfluxDB.
 *
 * @property bucket closure to generate the string for the bucket name
 * @property organization closure to generate the string for the organization name
 * @property points closure to generate a list of [Point]
 *
 */
@Spec
data class InfluxDbSavePointConfiguration<I>(
    var bucket: suspend (ctx: StepContext<*, *>, input: I) -> String = { _, _ -> "" },
    var organization: suspend (ctx: StepContext<*, *>, input: I) -> String = { _, _ -> "" },
    var points: suspend (ctx: StepContext<*, *>, input: I) -> List<Point> = { _, _ -> listOf() }
)

/**
 * Saves documents into InfluxDB.
 *
 */
fun <I> InfluxdbStepSpecification<*, I, *>.save(
    configurationBlock: InfluxDbSaveStepSpecification<I>.() -> Unit
): InfluxDbSaveStepSpecification<I> {
    val step = InfluxDbSaveStepSpecificationImpl<I>()
    step.configurationBlock()

    this.add(step)
    return step
}