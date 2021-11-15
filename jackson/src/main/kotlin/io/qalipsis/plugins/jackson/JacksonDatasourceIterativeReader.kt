package io.qalipsis.plugins.jackson

import com.fasterxml.jackson.databind.ObjectReader
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.steps.datasource.DatasourceIterativeReader
import java.io.InputStreamReader

/**
 * Implementation of a [DatasourceIterativeReader] for Jackson.
 *
 * @author Eric Jessé
 */
internal class JacksonDatasourceIterativeReader<R>(
        private val inputStreamReader: InputStreamReader,
        private val objectReader: ObjectReader
) : DatasourceIterativeReader<R> {

    lateinit var iterator: Iterator<R>

    override fun start(context: StepStartStopContext) {
        iterator = objectReader.readValues(inputStreamReader)
    }

    override suspend fun hasNext() = iterator.hasNext()

    override suspend fun next() = iterator.next()

}
