package io.qalipsis.plugins.jackson.csv

import assertk.Assert
import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.datasource.DatasourceRecord
import io.qalipsis.api.sync.Latch
import io.qalipsis.plugins.jackson.jackson
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.TestStepContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.opentest4j.AssertionFailedError

/**
 *
 * While tests in [CsvReaderStepSpecificationConverterTest] verify that the generated Jackson configuration is as
 * expected until the finest detail, [EndToEndCsvStepTest] aims at verifying that the mentioned configuration
 * actually does what we expect from it.
 *
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
internal class EndToEndCsvStepTest {

    val file = "test.csv"

    val converter = CsvReaderStepSpecificationConverter()

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    @Timeout(10)
    internal fun `should convert csv to map with comments allowed`() = testDispatcherProvider.runTest {
        val scenario = scenario("my-scenario") as StepSpecificationRegistry
        scenario.jackson().csvToMap {
            classpath(file)
            header {
                escapeChar('E')
                quoteChar('"')
                columnSeparator(';')
                withHeader()
                allowComments()

                column("1st value").double()
                column("2th value").string().array(",")
                column("3rd value").string(true)
                column("4th value").string(true)
            }
        }
        val result = executeStep<Map<String, *>>(this, scenario.rootSteps.first())

        // then
        assertThat(result).hasSize(3)
        result.forEachIndexed { index, value ->
            val indexValue = index + 1
            assertThat(value).isInstanceOf(DatasourceRecord::class).all {
                prop(DatasourceRecord<*>::ordinal).isEqualTo(index.toLong())
                prop(DatasourceRecord<*>::value).isNotNull().isInstanceOf(Map::class).all {
                    this as Assert<Map<String, *>>
                    key("1st value").isEqualTo(indexValue.toDouble())
                    key("2th value").isNotNull().isInstanceOf(List::class)
                        .isEqualTo(listOf("value${indexValue}-2-1", "value${indexValue}-2-2"))
                    key("3rd value").isEqualTo("value with ; $indexValue")
                    key("4th value").isEqualTo("\"escaped value $indexValue")
                }
            }
        }
    }

    @Test
    @Timeout(10)
    internal fun `should not convert csv to map without comments allowed`() = testDispatcherProvider.runTest {
        val scenario = scenario("my-scenario") as StepSpecificationRegistry
        scenario.jackson().csvToMap {
            classpath(file)
            header {
                escapeChar('E')
                quoteChar('"')
                columnSeparator(';')
                withHeader()

                column("1st value").double()
                column("2th value").string().array(",")
                column("3rd value").string(true)
                column("4th value").string(true)
            }
        }
        assertThrows<AssertionFailedError> {
            executeStep<Map<String, *>>(this, scenario.rootSteps.first())
        }
    }

    @Test
    @Timeout(10)
    internal fun `should not convert csv to map without comments allowed but no conversion`() =
        testDispatcherProvider.runTest {
            val scenario = scenario("my-scenario") as StepSpecificationRegistry
            scenario.jackson().csvToMap {
                classpath(file)
                header {
                    escapeChar('E')
                    quoteChar('"')
                    columnSeparator(';')
                    withHeader()
                }
            }
            val result = executeStep<Map<String, *>>(this, scenario.rootSteps.first())

            // then
            assertThat(result).hasSize(4)
        }

    @Test
    @Timeout(10)
    internal fun `should convert csv to list with comments allowed`() = testDispatcherProvider.runTest {
        val scenario = scenario("my-scenario") as StepSpecificationRegistry
        scenario.jackson().csvToList {
            classpath(file)
            header {
                escapeChar('E')
                quoteChar('"')
                columnSeparator(';')
                skipFirstDataRow()
                allowComments()

                column(0).double()
                column(1).string().array(",")
                column(2).string(true)
                column(3).string(true)
            }
        }
        val result = executeStep<List<*>>(this, scenario.rootSteps.first())

        // then
        assertThat(result).hasSize(3)
        result.forEachIndexed { index, value ->
            val indexValue = index + 1
            assertThat(value).isInstanceOf(DatasourceRecord::class).all {
                prop(DatasourceRecord<*>::ordinal).isEqualTo(index.toLong())
                prop(DatasourceRecord<*>::value).isNotNull().isInstanceOf(List::class).all {
                    index(0).isEqualTo(indexValue.toDouble())
                    index(1).isNotNull().isInstanceOf(List::class)
                        .isEqualTo(listOf("value${indexValue}-2-1", "value${indexValue}-2-2"))
                    index(2).isEqualTo("value with ; ${indexValue}")
                    index(3).isEqualTo("\"escaped value ${indexValue}")
                }
            }
        }
    }

    @Test
    @Timeout(10)
    internal fun `should convert csv to list without comments nor conversion`() = testDispatcherProvider.runTest {
        val scenario = scenario("my-scenario") as StepSpecificationRegistry
        scenario.jackson().csvToList {
            classpath(file)
            header {
                escapeChar('E')
                quoteChar('"')
                columnSeparator(';')

                column(0)
                column(1)
                column(2)
                column(3)
            }
        }
        val result = executeStep<List<*>>(this, scenario.rootSteps.first())

        // then
        assertThat(result).hasSize(5)
    }


    @Test
    @Timeout(10)
    internal fun `should convert csv to POJO with comments allowed`() = testDispatcherProvider.runTest {
        val scenario = scenario("my-scenario") as StepSpecificationRegistry
        scenario.jackson().csvToObject(PojoForTest::class) {
            classpath(file)
            header {
                escapeChar('E')
                quoteChar('"')
                columnSeparator(';')
                skipFirstDataRow()
                allowComments()

                column("theField1")
                column("theField2").array(",")
                column("theField3")
                column("theField4")
            }
        }
        val result = executeStep<PojoForTest>(this, scenario.rootSteps.first())

        // then
        assertThat(result).hasSize(3)
        result.forEachIndexed { index, value ->
            val indexValue = index + 1
            assertThat(value).isInstanceOf(DatasourceRecord::class).all {
                prop(DatasourceRecord<*>::ordinal).isEqualTo(index.toLong())
                prop(DatasourceRecord<*>::value).isNotNull().isInstanceOf(PojoForTest::class).all {
                    prop(PojoForTest::theField1).isEqualTo(indexValue.toDouble())
                    prop(PojoForTest::theField2).isInstanceOf(List::class)
                        .isEqualTo(listOf("value${indexValue}-2-1", "value${indexValue}-2-2"))
                    prop(PojoForTest::theField3).isEqualTo("value with ; ${indexValue}  ")
                    prop(PojoForTest::theField4).isEqualTo("\"escaped value ${indexValue}")
                }
            }
        }
    }

    private suspend fun <T : Any> executeStep(
        coroutineScope: CoroutineScope,
        specification: StepSpecification<*, *, *>
    ): List<DatasourceRecord<T?>> {
        val spec = specification as CsvReaderStepSpecification<T>
        val result = mutableListOf<DatasourceRecord<T?>>()
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), spec)
        val stepContext = TestStepContext<Unit, DatasourceRecord<T?>>(
            minionId = "",
            scenarioId = "",
            directedAcyclicGraphId = "",
            stepId = ""
        )

        val latch = Latch(true)
        coroutineScope.launch {
            for (outputRecord in stepContext.output as Channel) {
                result.add(outputRecord)
            }
            latch.release()
        }

        converter.convert<Unit, DatasourceRecord<T?>>(
            creationContext as StepCreationContext<CsvReaderStepSpecification<*>>
        )
        (creationContext.createdStep!! as Step<Unit, DatasourceRecord<T?>>).apply {
            start(relaxedMockk())
            execute(stepContext)
            stop(relaxedMockk())
            stepContext.output.close()
        }

        latch.await()

        assertThat(stepContext.errors).isEqualTo(emptyList())
        return result
    }

    data class PojoForTest(
        val theField1: Double,
        val theField2: List<String>,
        val theField3: String,
        val theField4: String
    )
}
