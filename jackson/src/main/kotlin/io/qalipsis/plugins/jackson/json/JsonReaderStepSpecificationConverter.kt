package io.qalipsis.plugins.jackson.json

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.micronaut.jackson.modules.BeanIntrospectionModule
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.exceptions.InvalidSpecificationException
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.datasource.DatasourceIterativeReader
import io.qalipsis.api.steps.datasource.DatasourceRecord
import io.qalipsis.api.steps.datasource.DatasourceRecordObjectConverter
import io.qalipsis.api.steps.datasource.IterativeDatasourceStep
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

    private fun <O : Any> convert(
        spec: JsonReaderStepSpecification<O>
    ): IterativeDatasourceStep<O, O, DatasourceRecord<O>> {
        return IterativeDatasourceStep(
            spec.name,
            createReader(spec), NoopDatasourceObjectProcessor(), DatasourceRecordObjectConverter()
        )
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
        mapper.registerModule(KotlinModule())
        mapper.registerModule(Jdk8Module())

        spec.mapperConfiguration(mapper)

        return mapper
    }

}
