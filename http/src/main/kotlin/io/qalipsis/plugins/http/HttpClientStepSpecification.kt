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

package io.qalipsis.plugins.http

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.http.configuration.findHttpApacheDefaults
import io.qalipsis.plugins.http.request.HttpRequest
import io.qalipsis.plugins.http.request.HttpRequestBuilder
import kotlin.reflect.KClass


interface HttpClientStepSpecification<INPUT, OUTPUT> :
    ConfigurableStepSpecification<INPUT, HttpResult<INPUT, OUTPUT>, HttpClientStepSpecification<INPUT, OUTPUT>> {

    /**
     * Configures the creation of the payload to send to the remote address, using the [StepContext] and the input received
     * from the previous step.
     */
    fun request(requestFactory: suspend HttpRequestBuilder.(StepContext<*, *>, INPUT) -> HttpRequest<*>)

    /**
     * Configures the connection to the remote address.
     */
    fun connect(configurationBlock: HttpClientConfiguration.() -> Unit)

    /**
     * Configures the monitoring of the step.
     */
    fun monitoring(configurationBlock: StepMonitoringConfiguration.() -> Unit)

    /**
     * Deserialize the body into the defined type using the first [io.qalipsis.plugins.http.response.HttpBodyDeserializer] matching the content type
     * of the response. Defaults implementations are proposed for JSON and XML, you can create your own implementations.
     */
    fun <T : Any> deserialize(type: KClass<T>): HttpClientStepSpecification<INPUT, T>

}

/**
 * Specification for a [HttpClientStep].
 *
 * @author Francisca Eze
 */
@Spec
internal class HttpClientStepSpecificationImpl<INPUT, OUTPUT> :
    AbstractStepSpecification<INPUT, HttpResult<INPUT, OUTPUT>, HttpClientStepSpecification<INPUT, OUTPUT>>(),
    HttpClientStepSpecification<INPUT, OUTPUT> {

    lateinit var requestFactory: suspend HttpRequestBuilder.(StepContext<*, *>, INPUT) -> HttpRequest<*>

    var bodyType: KClass<*> = String::class

    internal var connectionConfiguration = HttpClientConfiguration()

    internal var monitoringConfig = StepMonitoringConfiguration().all()

    override fun request(requestFactory: suspend HttpRequestBuilder.(StepContext<*, *>, INPUT) -> HttpRequest<*>) {
        this.requestFactory = requestFactory
    }

    override fun connect(configurationBlock: HttpClientConfiguration.() -> Unit) {
        connectionConfiguration.configurationBlock()
    }

    override fun monitoring(configurationBlock: StepMonitoringConfiguration.() -> Unit) {
        monitoringConfig.configurationBlock()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> deserialize(type: KClass<T>): HttpClientStepSpecification<INPUT, T> {
        bodyType = type
        return this as HttpClientStepSpecification<INPUT, T>
    }

}

/**
 * Create a new HTTP connection to send requests to a remote address.
 *
 * @author Francisca Eze
 */
fun <INPUT> HttpApachePluginSpecification<*, INPUT, *>.http(
    configurationBlock: HttpClientStepSpecification<INPUT, String>.() -> Unit
): HttpClientStepSpecification<INPUT, String> {
    val step = HttpClientStepSpecificationImpl<INPUT, String>()
    findHttpApacheDefaults(this as StepSpecification<*, *, *>)?.applyTo(step)
    step.configurationBlock()
    this.add(step)
    return step
}

/**
 * Create a new HTTP connection to send requests to a remote address.
 *
 * @author Francisca Eze
 */
fun HttpApacheScenarioSpecification.http(
    configurationBlock: HttpClientStepSpecification<Unit, String>.() -> Unit
): HttpClientStepSpecification<Unit, String> {
    val step = HttpClientStepSpecificationImpl<Unit, String>()
    findHttpApacheDefaults(this as StepSpecificationRegistry)?.applyTo(step)
    step.configurationBlock()
    (this as StepSpecificationRegistry).add(step)
    return step
}