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

package io.qalipsis.plugins.sql.save

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.sql.SqlConnection
import io.qalipsis.plugins.sql.SqlStepSpecification
import io.qalipsis.plugins.sql.configuration.findSqlDefaults
import io.qalipsis.plugins.sql.dialect.Protocol
import org.jetbrains.annotations.NotNull

/**
 * Specification for a [SqlSaveStep] to save records on the database.
 *
 * @author Carlos Vieira
 */
@Spec
interface SqlSaveStepSpecification<I> : StepSpecification<I, SqlSaveResult<I>, SqlSaveStepSpecification<I>>,
    ConfigurableStepSpecification<I, SqlSaveResult<I>, SqlSaveStepSpecification<I>>,
    SqlStepSpecification<I, SqlSaveResult<I>, SqlSaveStepSpecification<I>> {

    /**
     * Configures the pool of connections to the database.
     */
    fun connection(configBlock: SqlConnection.() -> Unit)

    /**
     * Defines the protocol to use to access and read from the database.
     */
    fun protocol(@NotNull protocol: Protocol)

    /**
     * Defines the name of the database table to do the insert.
     */
    fun tableName(tableNameFactory: suspend (ctx: StepContext<*, *>, input: I) -> String)

    /**
     * Defines the name of the columns to do the insert.
     */
    fun columns(columnsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<String>)

    /**
     * Defines the rows to be insert on save.
     */
    fun values(rowsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<SqlSaveRecord>)

    /**
     * Configures the monitoring of the save step.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

/**
 * Implementation of [SqlSaveStepSpecification].
 *
 * @author Carlos Vieira
 */
@Spec
internal class SqlSaveStepSpecificationImpl<I> :
    SqlSaveStepSpecification<I>,
    AbstractStepSpecification<I, SqlSaveResult<I>, SqlSaveStepSpecification<I>>() {

    internal var connection = SqlConnection()

    @field:NotNull
    internal var protocol: Protocol? = null

    internal var tableNameFactory: (suspend (ctx: StepContext<*, *>, input: I) -> String) = { _, _ -> "" }

    internal var columnsFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<String>) = { _, _ -> emptyList() }

    internal var rowsFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<SqlSaveRecord>) =
        { _, _ -> emptyList() }

    internal var monitoringConfig = StepMonitoringConfiguration()

    override fun connection(configBlock: SqlConnection.() -> Unit) {
        connection.configBlock()
    }

    override fun protocol(@NotNull protocol: Protocol) {
        this.protocol = protocol
    }

    override fun tableName(tableNameFactory: suspend (ctx: StepContext<*, *>, input: I) -> String) {
        this.tableNameFactory = tableNameFactory
    }

    override fun columns(columnsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<String>) {
        this.columnsFactory = columnsFactory
    }

    override fun values(rowsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<SqlSaveRecord>) {
        this.rowsFactory = rowsFactory
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }
}

/**
 * Creates a step to save data onto a SQL database and forwards the input to the next step.
 * Batch insertion is not supported, all records are saved one by one.
 *
 * @author Carlos Vieira
 */
fun <I> SqlStepSpecification<*, I, *>.save(
    configurationBlock: SqlSaveStepSpecification<I>.() -> Unit
): SqlSaveStepSpecification<I> {
    val step = SqlSaveStepSpecificationImpl<I>()
    findSqlDefaults(this as StepSpecification<*, *, *>)?.applyTo(step)
    step.configurationBlock()
    this.add(step)
    return step
}
