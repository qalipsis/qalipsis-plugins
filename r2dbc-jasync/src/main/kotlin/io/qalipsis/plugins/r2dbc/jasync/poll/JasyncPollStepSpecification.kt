package io.qalipsis.plugins.r2dbc.jasync.poll

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.BroadcastSpecification
import io.qalipsis.api.steps.LoopableSpecification
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.UnicastSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.datasource.DatasourceRecord
import io.qalipsis.plugins.r2dbc.jasync.Flattenable
import io.qalipsis.plugins.r2dbc.jasync.JasyncConnection
import io.qalipsis.plugins.r2dbc.jasync.R2dbcJasyncScenarioSpecification
import io.qalipsis.plugins.r2dbc.jasync.R2dbcJasyncStepSpecification
import io.qalipsis.plugins.r2dbc.jasync.dialect.Protocol
import org.jetbrains.annotations.NotNull
import java.time.Duration
import javax.validation.constraints.NotBlank

/**
 * Specification for an [io.qalipsis.api.steps.datasource.IterativeDatasourceStep] to poll data from a PostgreSQL,
 * MariaDB or MySQL database.
 *
 * The output is a list of [DatasourceRecord] contains maps of column names to values.
 *
 * When [flatten] is called, the records are provided one by one to the next step, otherwise each poll batch remains complete.
 *
 * @author Eric Jessé
 */
@Spec
interface JasyncPollStepSpecification :
    StepSpecification<Unit, List<DatasourceRecord<Map<String, Any?>>>, Flattenable<DatasourceRecord<Map<String, Any?>>>>,
    R2dbcJasyncStepSpecification<Unit, List<DatasourceRecord<Map<String, Any?>>>, Flattenable<DatasourceRecord<Map<String, Any?>>>>,
    LoopableSpecification, UnicastSpecification, BroadcastSpecification {

    /**
     * Configures the connection to the database.
     */
    fun connection(configBlock: JasyncConnection.() -> Unit)

    /**
     * Defines the protocol to use to access and read from the database.
     */
    fun protocol(@NotNull protocol: Protocol)

    /**
     * Defines the prepared statement to execute when polling. The query must contain ordering clauses, the tie-breaker
     * column being set as first column to sort.
     *
     * Placeholders for the where clause parameters have to be specified with a question mark  char: ?.
     *
     * The query is parsed using [Apache Calcite](https://calcite.apache.org). Make sure you escape the keywords
     * using the quote char supported by the underlying database - generally double-quote for PostgreSQL and back-tick
     * for MySQL and MariaDB. More about the keywords [here](https://calcite.apache.org/docs/reference.html#keywords).
     *
     * @param query a SQL prepared statement supported by the underlying database
     */
    fun query(@NotBlank query: String)

    /**
     * Ordered set of parameters to use for the placeholders in the [query]. The order and type of the parameters
     * must match the relevant placeholders and the related column.
     *
     * Java time entities are supported and will be converted to [Joda](https://www.joda.org) equivalent, which
     * as expected by Jasync.
     *
     * @param params list of typed arguments to populate the prepared statement.
     */
    fun parameters(vararg params: Any?)

    /**
     * Defines the name of the column to use as a tie-breaker, which is the value used to limit the records for the next poll.
     * The tie-breaker must be used as the first sort clause of the [query] and always be not null. Only the records
     * from the database having a [tieBreaker] greater (or less if sorted descending) than the last polled value will be fetched at next poll.
     *
     * Prefer to use a unique value and set [strict] to true to avoid duplicated records being polled.
     *
     * Important: the tie-breaker column has to be part of the selected column.
     *
     * @param tieBreaker name of the column used for the tie-breaker
     * @param strict when set to true, the where clause using the tie-breaker will apply a strict comparison, otherwise with equality
     */
    fun tieBreaker(@NotBlank tieBreaker: String, strict: Boolean = false)

    /**
     * Delay between two executions of poll.
     *
     * @param delay the delay to wait between the end of a poll and start of next one
     */
    fun pollDelay(delay: Duration)

    /**
    * Configures the monitoring of the poll step.
    */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

/**
 * Implementation of [JasyncPollStepSpecification].
 *
 * @author Eric Jessé
 */
@Spec
internal class JasyncPollStepSpecificationImpl :
    AbstractStepSpecification<Unit, List<DatasourceRecord<Map<String, Any?>>>, Flattenable<DatasourceRecord<Map<String, Any?>>>>(),
    Flattenable<DatasourceRecord<Map<String, Any?>>>, JasyncPollStepSpecification {

    override val singletonConfiguration: SingletonConfiguration = SingletonConfiguration(SingletonType.UNICAST)

    internal var connection = JasyncConnection()

    @field:NotNull
    internal var protocol: Protocol? = null

    @field:NotBlank
    internal var query: String? = null

    internal val parameters = mutableListOf<Any?>()

    @field:NotBlank
    internal var tieBreaker: String? = null

    internal var strictTieBreaker = false

    @field:NotNull
    internal var pollDelay: Duration? = null

    internal val monitoringConfig = StepMonitoringConfiguration()

    internal var flattenOutput = false

    override fun connection(configBlock: JasyncConnection.() -> Unit) {
        connection.configBlock()
    }

    override fun protocol(@NotNull protocol: Protocol) {
        this.protocol = protocol
    }

    override fun query(@NotBlank query: String) {
        this.query = query
    }

    override fun parameters(vararg params: Any?) {
        parameters.clear()
        parameters.addAll(params.toList())
    }

    override fun tieBreaker(tieBreaker: String, strict: Boolean) {
        this.tieBreaker = tieBreaker
        strictTieBreaker = strict
    }

    override fun pollDelay(delay: Duration) {
        this.pollDelay = delay
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

    override fun flatten(): StepSpecification<Unit, DatasourceRecord<Map<String, Any?>>, *> {
        flattenOutput = true

        @Suppress("UNCHECKED_CAST")
        return this as StepSpecification<Unit, DatasourceRecord<Map<String, Any?>>, *>
    }
}



/**
 * Creates a R2DBC-Jasync poll step in order to periodically fetch data from a PostgreSQL, MySQL or MariaDB database.
 *
 * This step is generally used in conjunction with a left join to assert data or inject them in a workflow.
 *
 * You can learn more on [jasync-sql website](https://github.com/jasync-sql/jasync-sql/wiki).
 *
 * @author Eric Jessé
 */
fun R2dbcJasyncScenarioSpecification.poll(
        configurationBlock: JasyncPollStepSpecification.() -> Unit
): Flattenable<DatasourceRecord<Map<String, Any?>>> {
    val step = JasyncPollStepSpecificationImpl()
    step.configurationBlock()

    (this as StepSpecificationRegistry).add(step)
    return step
}
