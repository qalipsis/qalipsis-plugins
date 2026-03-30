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
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.micronaut.jackson.modules.BeanIntrospectionModule
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.exceptions.InvalidSpecificationException
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.datasource.DatasourceIterativeReader
import io.qalipsis.api.steps.datasource.DatasourceRecord
import io.qalipsis.api.steps.datasource.DatasourceRecordObjectConverter
import io.qalipsis.api.steps.datasource.IterativeDatasourceStep
import io.qalipsis.api.steps.datasource.SequentialDatasourceStep
import io.qalipsis.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.qalipsis.plugins.jackson.JacksonDatasourceIterativeReader
import java.io.InputStreamReader

/**
 * [StepSpecificationConverter] from [JsonReaderStepSpecification] to [IterativeDatasourceStep] for a JSON data source.
 *
 * @author Maxim Golokhov
 */
@StepConverter
internal class JsonReaderStepSpecificationConverter : StepSpecificationConverter<JsonReaderStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is JsonReaderStepSpecification<*>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<JsonReaderStepSpecification<*>>) {
        creationContext.createdStep(convert(creationContext.stepSpecification as JsonReaderStepSpecification<out Any>))
    }

    private fun <O : Any> convert(spec: JsonReaderStepSpecification<O>): Step<*, DatasourceRecord<O>> {
        return if (spec.isReallySingleton) {
            IterativeDatasourceStep(
                spec.name,
                createReader(spec), NoopDatasourceObjectProcessor(), DatasourceRecordObjectConverter()
            )
        } else {
            SequentialDatasourceStep(
                spec.name,
                createReader(spec), NoopDatasourceObjectProcessor(), DatasourceRecordObjectConverter()
            )
        }
    }

    private fun <O : Any> createReader(spec: JsonReaderStepSpecification<O>): DatasourceIterativeReader<O> {
        val sourceUrl = spec.sourceConfiguration.url ?: throw InvalidSpecificationException("No source specified")
        return JacksonDatasourceIterativeReader(
            InputStreamReader(sourceUrl.openStream(), spec.sourceConfiguration.encoding),
            createMapper(spec).readerFor(spec.targetClass.java)
        )
    }

    private fun <O : Any> createMapper(spec: JsonReaderStepSpecification<O>): JsonMapper {
        val mapper = JsonMapper()
        mapper.registerModule(BeanIntrospectionModule())
        mapper.registerModule(JavaTimeModule())
        mapper.registerModule(KotlinModule.Builder().build())
        mapper.registerModule(Jdk8Module())

        spec.mapperConfiguration(mapper)

        return mapper
    }

}
