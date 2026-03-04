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

package io.qalipsis.plugins.sql.search

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.datasource.DatasourceRecord
import io.qalipsis.plugins.sql.SqlConnection
import io.qalipsis.plugins.sql.SqlStepSpecification
import io.qalipsis.plugins.sql.configuration.findSqlDefaults
import io.qalipsis.plugins.sql.dialect.Protocol
import org.jetbrains.annotations.NotNull

/**
 * Specification for a [SqlSearchStep] to search records from database.
 *
 * The output is a pair of [I] and a list of [DatasourceRecord] contains maps of column to values.
 *
 * @author Fiodar Hmyza
 */
@Spec
interface SqlSearchStepSpecification<I> :
    StepSpecification<I, SqlSearchBatchResults<I, Map<String, Any?>>, SqlSearchStepSpecification<I>>,
    ConfigurableStepSpecification<I, SqlSearchBatchResults<I, Map<String, Any?>>, SqlSearchStepSpecification<I>>,
    SqlStepSpecification<I, SqlSearchBatchResults<I, Map<String, Any?>>, SqlSearchStepSpecification<I>> {

    /**
     * Configures the pool of connections to the database.
     */
    fun connection(configBlock: SqlConnection.() -> Unit)

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
     * Configures the monitoring of the search step.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)

}

/**
 * Implementation of [SqlSearchStepSpecification].
 *
 * @author Fiodar Hmyza
 */
@Spec
internal class SqlSearchStepSpecificationImpl<I> :
    SqlSearchStepSpecification<I>,
    AbstractStepSpecification<I,SqlSearchBatchResults<I, Map<String, Any?>>, SqlSearchStepSpecification<I>>() {

    internal var connection = SqlConnection()

    @field:NotNull
    internal var protocol: Protocol? = null

    @field:NotNull
    internal var queryFactory: (suspend (ctx: StepContext<*, *>, input: I) -> String)? = null

    @field:NotNull
    internal var parametersFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<*>)? = null

    internal var monitoringConfig = StepMonitoringConfiguration()

    override fun connection(configBlock: SqlConnection.() -> Unit) {
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

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }
}


/**
 * Searches data in a SQL database.
 *
 * @author Fiodar Hmyza
 */
fun <I> SqlStepSpecification<*, I, *>.search(
    configurationBlock: SqlSearchStepSpecification<I>.() -> Unit
): SqlSearchStepSpecification<I> {
    val step = SqlSearchStepSpecificationImpl<I>()
    findSqlDefaults(this as StepSpecification<*, *, *>)?.applyTo(step)
    step.configurationBlock()
    this.add(step)
    return step
}
