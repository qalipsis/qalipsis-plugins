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

package io.qalipsis.plugins.jackson.json

import com.fasterxml.jackson.databind.json.JsonMapper
import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.plugins.jackson.AbstractJacksonStepSpecification
import io.qalipsis.plugins.jackson.JacksonScenarioSpecification
import io.qalipsis.plugins.jackson.JacksonStepSpecification
import kotlin.reflect.KClass

/**
 * Specification for a [io.qalipsis.api.steps.datasource.IterativeDatasourceStep] for JSON files.
 *
 * @property targetClass class to which the output lines should be mapped.
 *
 * @author Maxim Golokhov
 */
@Spec
data class JsonReaderStepSpecification<O : Any>(internal var targetClass: KClass<O>) :
    AbstractJacksonStepSpecification<O, JsonReaderStepSpecification<O>>() {

    internal var mapperConfiguration: (JsonMapper) -> Unit = {}

    /**
     * Tweaks the configuration of the [JsonMapper] used underneath.
     * Modules for Kotlin, Java time and JDK8 are registered before this closure is applied.
     *
     * @param mapperConfiguration lambda to configure [JsonMapper]
     */
    fun mapper(mapperConfiguration: (JsonMapper) -> Unit): JsonReaderStepSpecification<O> {
        this.mapperConfiguration = mapperConfiguration
        return this
    }
}

/**
 * Reads a JSON resource (file, classpath resource, URL) and returns each item as an instance of mappingClass.
 *
 * @author Maxim Golokhov
 */
fun <OUTPUT : Any> JacksonScenarioSpecification.jsonToObject(
        mappingClass: KClass<OUTPUT>,
        configurationBlock: JsonReaderStepSpecification<OUTPUT>.() -> Unit
): JsonReaderStepSpecification<OUTPUT> {
    val step = JsonReaderStepSpecification(mappingClass)
    step.configurationBlock()
    (this as StepSpecificationRegistry).add(step)
    return step
}

/**
 * Reads a JSON resource (file, classpath resource, URL) and returns each item as an instance of mappingClass.
 *
 * @author Maxim Golokhov
 */
fun <OUTPUT : Any> JacksonStepSpecification<*, *, *>.jsonToObject(
        mappingClass: KClass<OUTPUT>,
        configurationBlock: JsonReaderStepSpecification<OUTPUT>.() -> Unit
): JsonReaderStepSpecification<OUTPUT> {
    val step = JsonReaderStepSpecification(mappingClass)
    step.configurationBlock()
    this.add(step)
    return step
}

/**
 * Reads a JSON resource (file, classpath resource, URL) and returns each item as a [Map].
 *
 * @author Maxim Golokhov
 */
fun JacksonStepSpecification<*, *, *>.jsonToMap(
        configurationBlock: JsonReaderStepSpecification<Map<String, *>>.() -> Unit
): JsonReaderStepSpecification<Map<String, *>> {
    @Suppress("UNCHECKED_CAST")
    val step = JsonReaderStepSpecification(Map::class as KClass<Map<String, *>>)
    step.configurationBlock()
    this.add(step)
    return step
}

/**
 * Reads a JSON resource (file, classpath resource, URL) and returns each item as a [Map].
 *
 * @author Maxim Golokhov
 */
fun JacksonScenarioSpecification.jsonToMap(
        configurationBlock: JsonReaderStepSpecification<Map<String, *>>.() -> Unit
): JsonReaderStepSpecification<Map<String, *>> {
    @Suppress("UNCHECKED_CAST")
    val step = JsonReaderStepSpecification(Map::class as KClass<Map<String, *>>)
    step.configurationBlock()
    (this as StepSpecificationRegistry).add(step)
    return step
}
