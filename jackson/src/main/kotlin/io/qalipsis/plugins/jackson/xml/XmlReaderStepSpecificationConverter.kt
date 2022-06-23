package io.qalipsis.plugins.jackson.xml

import com.fasterxml.jackson.dataformat.xml.XmlMapper
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
 * [StepSpecificationConverter] from [XmlReaderStepSpecification] to [IterativeDatasourceStep] for a XML data source.
 *
 * @author Maxim Golokhov
 */
@StepConverter
internal class XmlReaderStepSpecificationConverter : StepSpecificationConverter<XmlReaderStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is XmlReaderStepSpecification<*>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<XmlReaderStepSpecification<*>>) {
        creationContext.createdStep(convert(creationContext.stepSpecification as XmlReaderStepSpecification<out Any>))
    }

    private fun <O : Any> convert(spec: XmlReaderStepSpecification<O>): Step<*, DatasourceRecord<O>> {
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

    private fun <O : Any> createReader(spec: XmlReaderStepSpecification<O>): DatasourceIterativeReader<O> {
        val sourceUrl = spec.sourceConfiguration.url ?: throw InvalidSpecificationException("No source specified")
        return JacksonDatasourceIterativeReader(
            InputStreamReader(sourceUrl.openStream(), spec.sourceConfiguration.encoding),
            createMapper(spec).readerFor(spec.targetClass.java)
        )
    }

    private fun <O : Any> createMapper(spec: XmlReaderStepSpecification<O>): XmlMapper {
        val mapper = XmlMapper()
        mapper.registerModule(BeanIntrospectionModule())
        mapper.registerModule(JavaTimeModule())
        mapper.registerModule(KotlinModule())
        mapper.registerModule(Jdk8Module())

        spec.mapperConfiguration(mapper)

        return mapper
    }

}
