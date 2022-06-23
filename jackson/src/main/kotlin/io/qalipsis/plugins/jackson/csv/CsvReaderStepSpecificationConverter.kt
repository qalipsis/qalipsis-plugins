package io.qalipsis.plugins.jackson.csv

import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvSchema
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.jackson.modules.BeanIntrospectionModule
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.exceptions.InvalidSpecificationException
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.datasource.DatasourceIterativeReader
import io.qalipsis.api.steps.datasource.DatasourceObjectProcessor
import io.qalipsis.api.steps.datasource.DatasourceRecord
import io.qalipsis.api.steps.datasource.DatasourceRecordObjectConverter
import io.qalipsis.api.steps.datasource.IterativeDatasourceStep
import io.qalipsis.api.steps.datasource.SequentialDatasourceStep
import io.qalipsis.api.steps.datasource.processors.MapDatasourceObjectProcessor
import io.qalipsis.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.qalipsis.plugins.jackson.JacksonDatasourceIterativeReader
import java.io.InputStreamReader
import kotlin.reflect.full.isSuperclassOf

/**
 * [StepSpecificationConverter] from [CsvReaderStepSpecification] to [IterativeDatasourceStep] for a CSV data source.
 *
 * @author Eric Jessé
 */
@StepConverter
internal class CsvReaderStepSpecificationConverter : StepSpecificationConverter<CsvReaderStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is CsvReaderStepSpecification<*>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<CsvReaderStepSpecification<*>>) {
        creationContext.createdStep(convert(creationContext.stepSpecification as CsvReaderStepSpecification<out Any>))
    }

    private fun <O : Any> convert(spec: CsvReaderStepSpecification<O>): Step<*, DatasourceRecord<O>> {
        return if (spec.isReallySingleton) {
            IterativeDatasourceStep(
                spec.name,
                createReader(spec), createProcessor(spec), DatasourceRecordObjectConverter()
            )
        } else {
            SequentialDatasourceStep(
                spec.name,
                createReader(spec), createProcessor(spec), DatasourceRecordObjectConverter()
            )
        }
    }

    private fun <O : Any> createReader(spec: CsvReaderStepSpecification<O>): DatasourceIterativeReader<O> {
        val sourceUrl = spec.sourceConfiguration.url ?: throw InvalidSpecificationException("No source specified")
        val targetClass = if (List::class.isSuperclassOf(spec.targetClass)) LinkedHashMap::class else spec.targetClass
        return JacksonDatasourceIterativeReader(
            InputStreamReader(sourceUrl.openStream(), spec.sourceConfiguration.encoding),
            createMapper().readerFor(targetClass.java).with(createSchema(spec))
        )
    }

    private fun <O : Any> createSchema(spec: CsvReaderStepSpecification<O>): CsvSchema {
        val schemaBuilder = CsvSchema.builder()
        spec.headerConfiguration.columns.filterNotNull().forEach { column ->
            if (column.isArray) {
                schemaBuilder.addColumn(
                    CsvSchema.Column(
                        column.index, column.name, CsvSchema.ColumnType.ARRAY,
                        column.listSeparator
                    )
                )
            } else {
                schemaBuilder.addColumn(
                    CsvSchema.Column(column.index, column.name, CsvSchema.ColumnType.STRING)
                )
            }
        }
        schemaBuilder
            .setLineSeparator(spec.parsingConfiguration.lineSeparator)
            .setColumnSeparator(spec.parsingConfiguration.columnSeparator)
            .setEscapeChar(spec.parsingConfiguration.escapeChar)
            .setQuoteChar(spec.parsingConfiguration.quoteChar)
            .setAllowComments(spec.parsingConfiguration.allowComments)
            .setSkipFirstDataRow(spec.headerConfiguration.skipFirstDataRow)
            .setUseHeader(spec.headerConfiguration.withHeader)

        return schemaBuilder.build()
    }

    @KTestable
    private fun createMapper() = CsvMapper().also { mapper ->
        mapper.findAndRegisterModules()
        mapper.registerModule(KotlinModule())
        mapper.registerModule(BeanIntrospectionModule())
    }

    @Suppress("UNCHECKED_CAST")
    private fun <O : Any> createProcessor(spec: CsvReaderStepSpecification<O>): DatasourceObjectProcessor<O, O> {
        return when {
            List::class.isSuperclassOf(spec.targetClass) -> createListProcessor(
                spec as CsvReaderStepSpecification<List<*>>
            )
            Map::class.isSuperclassOf(spec.targetClass) -> createMapProcessor(
                spec as CsvReaderStepSpecification<Map<String, *>>
            )
            else -> NoopDatasourceObjectProcessor()
        }
    }

    private fun <O> createListProcessor(spec: CsvReaderStepSpecification<List<*>>): DatasourceObjectProcessor<O, O> {
        val columnConversionByIndex = mutableListOf<((Any?) -> Any?)>()
        spec.headerConfiguration.columns.filterNotNull().forEach { column ->
            columnConversionByIndex.add(column.index, buildConverter(column))
        }
        @Suppress("UNCHECKED_CAST")
        return LinkedHashMapToListObjectProcessor(columnConversionByIndex) as DatasourceObjectProcessor<O, O>
    }

    private fun <O> createMapProcessor(
        spec: CsvReaderStepSpecification<Map<String, Any?>>
    ): DatasourceObjectProcessor<O, O> {
        val columnConversionByName = mutableMapOf<String, ((Any?) -> Any?)>()
        spec.headerConfiguration.columns.filterNotNull().forEach { column ->
            columnConversionByName[column.name] = buildConverter(column)
        }
        @Suppress("UNCHECKED_CAST")
        return MapDatasourceObjectProcessor(columnConversionByName) as DatasourceObjectProcessor<O, O>
    }

    private fun buildConverter(column: CsvColumnConfiguration<*>): (Any?.() -> Any?) {
        val typeConverter = column.type.converter

        var converter: ((Any?) -> Any?) = if (column.trim) {
            { value ->
                typeConverter((value as String?)?.trim())
            }
        } else {
            { value ->
                typeConverter(value as String?)
            }
        }

        if (column.isArray) {
            // Converts each value for the list.
            val previousConverter = converter
            converter = { value ->
                when (value) {
                    is Iterable<*> -> {
                        value.map {
                            previousConverter(it)
                        }
                    }
                    is Array<*> -> {
                        value.map {
                            previousConverter(it)
                        }.toTypedArray()
                    }
                    else -> {
                        value
                    }
                }
            }
        }

        // Surrounds with the error management.
        if (column.ignoreError) {
            val previousConverter = converter
            converter = { value ->
                try {
                    previousConverter(value)
                } catch (e: Exception) {
                    column.defaultValue
                }
            }
        }
        return converter
    }

}
