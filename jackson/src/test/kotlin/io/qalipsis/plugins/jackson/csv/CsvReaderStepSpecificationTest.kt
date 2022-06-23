package io.qalipsis.plugins.jackson.csv

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.DummyStepSpecification
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.plugins.jackson.config.SourceConfiguration
import io.qalipsis.plugins.jackson.jackson
import io.qalipsis.test.assertk.prop
import org.junit.jupiter.api.Test
import java.net.URL
import java.nio.file.Path
import java.time.Duration

/**
 *
 * @author Eric Jessé
 */
internal class CsvReaderStepSpecificationTest {

    @Test
    internal fun `should add minimal specification to the scenario that generates an array`() {
        val scenario = scenario("my-scenario") as StepSpecificationRegistry
        scenario.jackson().csvToList {
            file("/path/to/my/file")
        }

        assertThat(scenario.rootSteps[0]).isInstanceOf(CsvReaderStepSpecification::class).all {
            prop(CsvReaderStepSpecification<*>::sourceConfiguration).isDataClassEqualTo(SourceConfiguration(
                    url = Path.of("/path/to/my/file").toUri().toURL()
            ))
            prop("targetClass").isEqualTo(List::class)
            prop(CsvReaderStepSpecification<*>::singletonConfiguration).isDataClassEqualTo(
                SingletonConfiguration(SingletonType.SEQUENTIAL)
            )
            prop(CsvReaderStepSpecification<*>::parsingConfiguration).isDataClassEqualTo(
                    CsvParsingConfiguration())
            prop(CsvReaderStepSpecification<*>::headerConfiguration).isDataClassEqualTo(
                    CsvHeaderConfiguration())
        }
    }

    @Test
    internal fun `should add minimal specification to the scenario that generates a map`() {
        val scenario = scenario("my-scenario") as StepSpecificationRegistry
        scenario.jackson().csvToMap {
            classpath("/path/to/my/file")
        }

        assertThat(scenario.rootSteps[0]).isInstanceOf(CsvReaderStepSpecification::class).all {
            prop("targetClass").isEqualTo(Map::class)
            prop(CsvReaderStepSpecification<*>::sourceConfiguration).isDataClassEqualTo(
                    SourceConfiguration(
                            url = this::class.java.getResource("path/to/my/file")
                    ))
            prop(CsvReaderStepSpecification<*>::singletonConfiguration).isDataClassEqualTo(
                SingletonConfiguration(SingletonType.SEQUENTIAL)
            )
            prop(CsvReaderStepSpecification<*>::parsingConfiguration).isDataClassEqualTo(
                    CsvParsingConfiguration())
            prop(CsvReaderStepSpecification<*>::headerConfiguration).isDataClassEqualTo(
                    CsvHeaderConfiguration())
        }
    }

    @Test
    internal fun `should add minimal specification to the scenario that generates an object`() {
        val scenario = scenario("my-scenario") as StepSpecificationRegistry
        scenario.jackson().csvToObject(MyPojo::class) {
            url("http://path/to/my/file")
        }

        assertThat(scenario.rootSteps[0]).isInstanceOf(CsvReaderStepSpecification::class).all {
            prop("targetClass").isEqualTo(MyPojo::class)
            prop(CsvReaderStepSpecification<*>::sourceConfiguration).isDataClassEqualTo(SourceConfiguration(
                    url = URL("http://path/to/my/file")
            ))
            prop(CsvReaderStepSpecification<*>::singletonConfiguration).isDataClassEqualTo(
                SingletonConfiguration(SingletonType.SEQUENTIAL)
            )
            prop(CsvReaderStepSpecification<*>::parsingConfiguration).isDataClassEqualTo(CsvParsingConfiguration())
            prop(CsvReaderStepSpecification<*>::headerConfiguration).isDataClassEqualTo(CsvHeaderConfiguration())
        }
    }

    @Test
    internal fun `should add minimal specification that generates an array as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().csvToList {
            file("/path/to/my/file")
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(CsvReaderStepSpecification::class).all {
            prop("targetClass").isEqualTo(List::class)
            prop(CsvReaderStepSpecification<*>::sourceConfiguration).isDataClassEqualTo(SourceConfiguration(
                    url = Path.of("/path/to/my/file").toUri().toURL()
            ))
            prop(CsvReaderStepSpecification<*>::singletonConfiguration).isDataClassEqualTo(
                SingletonConfiguration(SingletonType.SEQUENTIAL)
            )
            prop(CsvReaderStepSpecification<*>::parsingConfiguration).isDataClassEqualTo(
                    CsvParsingConfiguration())
            prop(CsvReaderStepSpecification<*>::headerConfiguration).isDataClassEqualTo(
                    CsvHeaderConfiguration())
        }
    }

    @Test
    internal fun `should add minimal specification that generates a map as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().csvToMap {
            classpath("/path/to/my/file")
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(CsvReaderStepSpecification::class).all {
            prop("targetClass").isEqualTo(Map::class)
            prop(CsvReaderStepSpecification<*>::sourceConfiguration).isDataClassEqualTo(
                    SourceConfiguration(
                            url = this::class.java.getResource("path/to/my/file")
                    ))
            prop(CsvReaderStepSpecification<*>::singletonConfiguration).isDataClassEqualTo(
                SingletonConfiguration(SingletonType.SEQUENTIAL)
            )
            prop(CsvReaderStepSpecification<*>::parsingConfiguration).isDataClassEqualTo(
                    CsvParsingConfiguration())
            prop(CsvReaderStepSpecification<*>::headerConfiguration).isDataClassEqualTo(
                    CsvHeaderConfiguration())
        }
    }

    @Test
    internal fun `should add minimal specification that generates a POJO as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().csvToObject(MyPojo::class) {
            url("http://path/to/my/file")
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(CsvReaderStepSpecification::class).all {
            prop("targetClass").isEqualTo(MyPojo::class)
            prop(CsvReaderStepSpecification<*>::sourceConfiguration).isDataClassEqualTo(SourceConfiguration(
                    url = URL("http://path/to/my/file")
            ))
            prop(CsvReaderStepSpecification<*>::singletonConfiguration).isDataClassEqualTo(
                SingletonConfiguration(SingletonType.SEQUENTIAL)
            )
            prop(CsvReaderStepSpecification<*>::parsingConfiguration).isDataClassEqualTo(CsvParsingConfiguration())
            prop(CsvReaderStepSpecification<*>::headerConfiguration).isDataClassEqualTo(CsvHeaderConfiguration())
        }
    }

    @Test
    internal fun `should configure the parsing`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().csvToObject(MyPojo::class) {
            lineSeparator('L')
            columnSeparator('C')
            quoteChar('Q')
            escapeChar('E')
            allowComments()
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(CsvReaderStepSpecification::class).all {
            prop(CsvReaderStepSpecification<*>::parsingConfiguration).isDataClassEqualTo(CsvParsingConfiguration(
                    lineSeparator = "L",
                    columnSeparator = 'C',
                    quoteChar = 'Q',
                    escapeChar = 'E',
                    allowComments = true
            ))
        }
    }

    @Test
    internal fun `should configure the header`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().csvToObject(MyPojo::class) {
            header {
                column("my-column").boolean()
                column("my-column2").boolean()
                column("my-column3").boolean()
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(CsvReaderStepSpecification::class).all {
            prop(CsvReaderStepSpecification<*>::headerConfiguration).all {
                prop(CsvHeaderConfiguration::columns).hasSize(3)
            }
        }
    }

    @Test
    internal fun `should configure the singleton as default loop`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().csvToObject(MyPojo::class) {
            loop()
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(CsvReaderStepSpecification::class).all {
            prop(CsvReaderStepSpecification<*>::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.LOOP)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
            }
        }
    }

    @Test
    internal fun `should configure the singleton as loop with specified timeout`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().csvToObject(MyPojo::class) {
            loop(Duration.ofDays(3))
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(CsvReaderStepSpecification::class).all {
            prop(CsvReaderStepSpecification<*>::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.LOOP)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ofDays(3))
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
            }
        }
    }

    @Test
    internal fun `should configure the singleton as default broadcast`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().csvToObject(MyPojo::class) {
            broadcast()
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(CsvReaderStepSpecification::class).all {
            prop(CsvReaderStepSpecification<*>::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.BROADCAST)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
            }
        }
    }

    @Test
    internal fun `should configure the singleton as broadcast with specified timeout and buffer`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().csvToObject(MyPojo::class) {
            broadcast(123, Duration.ofDays(3))
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(CsvReaderStepSpecification::class).all {
            prop(CsvReaderStepSpecification<*>::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.BROADCAST)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ofDays(3))
                prop(SingletonConfiguration::bufferSize).isEqualTo(123)
            }
        }
    }

    @Test
    internal fun `should configure the singleton as default unicast`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().csvToObject(MyPojo::class) {
            unicast()
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(CsvReaderStepSpecification::class).all {
            prop(CsvReaderStepSpecification<*>::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
            }
        }
    }

    @Test
    internal fun `should configure the singleton as unicast with specified timeout and buffer`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().csvToObject(MyPojo::class) {
            unicast(123, Duration.ofDays(3))
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(CsvReaderStepSpecification::class).all {
            prop(CsvReaderStepSpecification<*>::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ofDays(3))
                prop(SingletonConfiguration::bufferSize).isEqualTo(123)
            }
        }
    }

    data class MyPojo(
            val field: String
    )
}
