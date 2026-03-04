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

package io.qalipsis.plugins.sql.poll

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.BroadcastSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.LoopableSpecification
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.UnicastSpecification
import io.qalipsis.api.steps.datasource.DatasourceRecord
import io.qalipsis.plugins.sql.SqlConnection
import io.qalipsis.plugins.sql.SqlScenarioSpecification
import io.qalipsis.plugins.sql.SqlStepSpecification
import io.qalipsis.plugins.sql.configuration.findSqlDefaults
import io.qalipsis.plugins.sql.dialect.Protocol
import java.time.Duration
import javax.validation.constraints.NotBlank
import org.jetbrains.annotations.NotNull

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
interface SqlPollStepSpecification :
    StepSpecification<Unit, List<DatasourceRecord<Map<String, Any?>>>, SqlPollStepSpecification>,
    ConfigurableStepSpecification<Unit, List<DatasourceRecord<Map<String, Any?>>>, SqlPollStepSpecification>,
    SqlStepSpecification<Unit, List<DatasourceRecord<Map<String, Any?>>>, SqlPollStepSpecification>,
    LoopableSpecification, UnicastSpecification, BroadcastSpecification {

    /**
     * Configures the connection to the database.
     */
    fun connection(configBlock: SqlConnection.() -> Unit)

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
     * @param params list of typed arguments to populate the prepared statement.
     */
    fun parameters(vararg params: Any?)

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
    fun flatten(): StepSpecification<Unit, DatasourceRecord<Map<String, Any?>>, *>
}

/**
 * Implementation of [SqlPollStepSpecification].
 *
 * @author Eric Jessé
 */
@Spec
internal class SqlPollStepSpecificationImpl :
    AbstractStepSpecification<Unit, List<DatasourceRecord<Map<String, Any?>>>, SqlPollStepSpecification>(),
    SqlPollStepSpecification {

    override val singletonConfiguration: SingletonConfiguration = SingletonConfiguration(SingletonType.UNICAST)

    internal var connection = SqlConnection()

    @field:NotNull
    internal var protocol: Protocol? = null

    @field:NotBlank
    internal var query: String? = null

    internal val parameters = mutableListOf<Any?>()

    @field:NotNull
    internal var pollDelay: Duration? = null

    internal var flattenOutput = false

    internal var monitoringConfig = StepMonitoringConfiguration()

    override fun connection(configBlock: SqlConnection.() -> Unit) {
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

    override fun pollDelay(delay: Duration) {
        this.pollDelay = delay
    }

    override fun flatten(): StepSpecification<Unit, DatasourceRecord<Map<String, Any?>>, *> {
        flattenOutput = true

        @Suppress("UNCHECKED_CAST")
        return this as StepSpecification<Unit, DatasourceRecord<Map<String, Any?>>, *>
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }
}

/**
 * Creates a SQL poll step in order to periodically fetch data from a PostgreSQL, MySQL or MariaDB database.
 *
 * This step is generally used in conjunction with a left join to assert data or inject them in a workflow.
 *
 * @author Eric Jessé
 */
fun SqlScenarioSpecification.poll(
    configurationBlock: SqlPollStepSpecification.() -> Unit
): SqlPollStepSpecification {
    val step = SqlPollStepSpecificationImpl()
    findSqlDefaults(this as StepSpecificationRegistry)?.applyTo(step)
    step.configurationBlock()

    (this as StepSpecificationRegistry).add(step)
    return step
}
