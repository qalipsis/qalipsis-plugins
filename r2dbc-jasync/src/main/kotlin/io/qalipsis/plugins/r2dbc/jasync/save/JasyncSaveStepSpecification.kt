package io.qalipsis.plugins.r2dbc.jasync.save

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.r2dbc.jasync.JasyncConnection
import io.qalipsis.plugins.r2dbc.jasync.R2dbcJasyncStepSpecification
import io.qalipsis.plugins.r2dbc.jasync.dialect.Protocol
import org.jetbrains.annotations.NotNull

/**
 * Specification for a [io.qalipsis.plugins.r2dbc.jasync.search.JasyncSaveStep] to save records on the database.
 *
 * @author Carlos Vieira
 */
@Spec
interface JasyncSaveStepSpecification<I> : StepSpecification<I, JasyncSaveResult<I>, JasyncSaveStepSpecification<I>>,
    R2dbcJasyncStepSpecification<I, JasyncSaveResult<I>, JasyncSaveStepSpecification<I>> {

    /**
     * Configures the pool of connections to the database.
     */
    fun connection(configBlock: JasyncConnection.() -> Unit)

    /**
     * Defines the protocol to use to access and read from the database.
     */
    fun protocol(@NotNull protocol: Protocol)

    /**
     * Defines the name of the database table to do the insert.
     */
    fun tableNameFactory(tableNameFactory: suspend (ctx: StepContext<*, *>, input: I) -> String)

    /**
     * Defines the name of the columns to do the insert.
     */
    fun columnsFactory(columnsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<String>)

    /**
     * Defines the rows to be insert on save.
     */
    fun rowsFactory(rowsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<JasyncSaverRecord>)

    /**
     * Configures the monitoring of the consume step.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

/**
 * Implementation of [JasyncSaveStepSpecification].
 *
 * @author Carlos Vieira
 */
@Spec
internal class JasyncSaveStepSpecificationImpl<I> :
    JasyncSaveStepSpecification<I>,
    AbstractStepSpecification<I, JasyncSaveResult<I>, JasyncSaveStepSpecification<I>>() {

    internal var connection = JasyncConnection()

    @field:NotNull
    internal var protocol: Protocol? = null

    internal var tableNameFactory: (suspend (ctx: StepContext<*, *>, input: I) -> String) = { _, _ -> "" }

    internal var columnsFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<String>) = { _, _ -> emptyList() }

    internal var rowsFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<JasyncSaverRecord>) =
        { _, _ -> emptyList() }

    internal val metrics = JasyncSaveMetricsConfiguration()

    internal val monitoringConfig = StepMonitoringConfiguration()

    override fun connection(configBlock: JasyncConnection.() -> Unit) {
        connection.configBlock()
    }

    override fun protocol(@NotNull protocol: Protocol) {
        this.protocol = protocol
    }

    override fun tableNameFactory(tableNameFactory: suspend (ctx: StepContext<*, *>, input: I) -> String) {
        this.tableNameFactory = tableNameFactory
    }

    override fun columnsFactory(columnsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<String>) {
        this.columnsFactory = columnsFactory
    }

    override fun rowsFactory(rowsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<JasyncSaverRecord>) {
        this.rowsFactory = rowsFactory
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }
}

/**
 * Configuration of the metrics to record for the Jasync save step.
 *
 * @property recordsCount when true, records the number of saved records.
 * @property timeToSuccess when true, records the time of response of a success save.
 * @property timeToFailure when true, records the time of response of a failure save.
 * @property successCount when true, records the number of successful saves.
 * @property failureCount when true, records the number of failed saves.
 *
 * @author Carlos Vieira
 */
@Spec
data class JasyncSaveMetricsConfiguration(
    var recordsCount: Boolean = false,
    val timeToSuccess: Boolean = false,
    val timeToFailure: Boolean = false,
    var successCount: Boolean = false,
    var failureCount: Boolean = false
)

/**
 * Creates a step to save data onto a SQL database and forwards the input to the next step.
 * Batch insertion is not supported, all records are save one by one.
 *
 * You can learn more on [Jasync website](https://github.com/jasync-sql/jasync-sql).
 *
 * @author Carlos Vieira
 */
fun <I> R2dbcJasyncStepSpecification<*, I, *>.save(
    configurationBlock: JasyncSaveStepSpecification<I>.() -> Unit
): JasyncSaveStepSpecification<I> {
    val step = JasyncSaveStepSpecificationImpl<I>()
    step.configurationBlock()
    this.add(step)
    return step
}
