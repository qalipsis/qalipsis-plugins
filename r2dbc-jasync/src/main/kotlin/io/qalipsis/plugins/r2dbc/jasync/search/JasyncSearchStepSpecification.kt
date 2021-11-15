package io.qalipsis.plugins.r2dbc.jasync.search

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.datasource.DatasourceRecord
import io.qalipsis.plugins.r2dbc.jasync.JasyncConnection
import io.qalipsis.plugins.r2dbc.jasync.R2dbcJasyncStepSpecification
import io.qalipsis.plugins.r2dbc.jasync.dialect.Protocol
import org.jetbrains.annotations.NotNull

/**
 * Specification for a [io.qalipsis.plugins.r2dbc.jasync.search.JasyncSearchStep] to search records from database.
 *
 * The output is a pair of [I] and a list of [DatasourceRecord] contains maps of column to values.
 *
 * When [flatten] it is a map of column to values.
 *
 * @author Fiodar Hmyza
 */
@Spec
interface JasyncSearchStepSpecification<I> :
    StepSpecification<I, Pair<I, List<DatasourceRecord<Map<String, Any?>>>>, JasyncSearchStepSpecification<I>>,
    R2dbcJasyncStepSpecification<I, Pair<I, List<DatasourceRecord<Map<String, Any?>>>>, JasyncSearchStepSpecification<I>> {

    /**
     * Configures the pool of connections to the database.
     */
    fun connection(configBlock: JasyncConnection.() -> Unit)

    /**
     * Defines the protocol to use to access and read from the database.
     */
    fun protocol(@NotNull protocol: Protocol)

    /**
     * Defines the prepared statement to execute when searching from the database.
     */
    fun query(queryBuilder: suspend (ctx: StepContext<*, *>, input: I) -> String)

    /**
     * Builder for the options to add as query parameters
     */
    fun parameters(parametersBuilder: suspend (ctx: StepContext<*, *>, input: I) -> List<*>)

    /**
     * Configures the metrics of the poll step.
     */
    fun metrics(metricsConfiguration: JasyncSearchMetricsConfiguration.() -> Unit)

    /**
     * Returns each record of a batch individually to the next steps.
     */
    fun flatten(): StepSpecification<I, DatasourceRecord<Map<String, Any?>>, *>
}

/**
 * Implementation of [JasyncSearchStepSpecification].
 *
 * @author Fiodar Hmyza
 */
@Spec
internal class JasyncSearchStepSpecificationImpl<I> :
    JasyncSearchStepSpecification<I>,
    AbstractStepSpecification<I, Pair<I, List<DatasourceRecord<Map<String, Any?>>>>, JasyncSearchStepSpecification<I>>() {

    internal var connection = JasyncConnection()

    @field:NotNull
    internal var protocol: Protocol? = null

    @field:NotNull
    internal var queryFactory: (suspend (ctx: StepContext<*, *>, input: I) -> String)? = null

    @field:NotNull
    internal var parametersFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<*>)? = null

    internal val metrics = JasyncSearchMetricsConfiguration()

    internal var flattenOutput = false

    override fun connection(configBlock: JasyncConnection.() -> Unit) {
        connection.configBlock()
    }

    override fun protocol(@NotNull protocol: Protocol) {
        this.protocol = protocol
    }

    override fun query(queryBuilder: suspend (ctx: StepContext<*, *>, input: I) -> String) {
        this.queryFactory = queryBuilder
    }

    override fun parameters(parametersBuilder: suspend (ctx: StepContext<*, *>, input: I) -> List<*>) {
        this.parametersFactory = parametersBuilder
    }

    override fun metrics(metricsConfiguration: JasyncSearchMetricsConfiguration.() -> Unit) {
        this.metrics.metricsConfiguration()
    }

    override fun flatten(): StepSpecification<I, DatasourceRecord<Map<String, Any?>>, *> {
        flattenOutput = true

        @Suppress("UNCHECKED_CAST")
        return this as StepSpecification<I, DatasourceRecord<Map<String, Any?>>, *>
    }

}


/**
 * Configuration of the metrics to record for the Jasync search step.
 *
 * @property recordsCount when true, records the number of received records.
 *
 * @author Fiodar Hmyza
 */
@Spec
data class JasyncSearchMetricsConfiguration(
    var recordsCount: Boolean = false
)

/**
 * Searches data in a SQL database.
 *
 * @author Fiodar Hmyza
 */
fun <I> R2dbcJasyncStepSpecification<*, I, *>.search(
    configurationBlock: JasyncSearchStepSpecification<I>.() -> Unit
): JasyncSearchStepSpecification<I> {
    val step = JasyncSearchStepSpecificationImpl<I>()
    step.configurationBlock()
    this.add(step)
    return step
}
