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

package io.qalipsis.plugins.http.connectionProvider

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import org.apache.hc.client5.http.async.HttpAsyncClient

/**
 * Provides options to create, acquire and release HTTP connections to minions.
 *
 * @author Francisca Eze
 */
interface ConnectionProvider {

    /**
     * Initializes the connection provider.
     */
    fun init( context: StepStartStopContext) = Unit

    /**
     * Acquires a connection for a given minion.
     */
    fun acquire(context: StepContext<*, *>): HttpAsyncClient

    /**
     * Releases the connection after use.
     */
    fun release(context: StepContext<*, *>, client: HttpAsyncClient) = Unit

    /**
     * Shuts down the client.
     */
    fun shutdown(context: StepStartStopContext) = Unit
}