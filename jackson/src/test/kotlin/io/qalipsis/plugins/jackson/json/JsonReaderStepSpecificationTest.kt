package io.qalipsis.plugins.jackson.json

import assertk.all
import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.DummyStepSpecification
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.plugins.jackson.config.SourceConfiguration
import io.qalipsis.plugins.jackson.jackson
import io.qalipsis.test.assertk.prop
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.net.URL
import java.time.Duration

/**
 * @author Maxim Golokhov
 */
internal class JsonReaderStepSpecificationTest {

    @Test
    internal fun `should add minimal specification to the scenario that generates an object`() {
        val scenario = scenario("my-scenario") as StepSpecificationRegistry
        scenario.jackson().jsonToObject(MyPojo::class) {
            url("http://path/to/my/file")
        }

        assertThat(scenario.rootSteps[0]).isInstanceOf(JsonReaderStepSpecification::class).all {
            prop("targetClass").isEqualTo(MyPojo::class)
            prop(JsonReaderStepSpecification<*>::sourceConfiguration).isDataClassEqualTo(SourceConfiguration(
                    url = URL("http://path/to/my/file")
            ))
            prop(JsonReaderStepSpecification<*>::singletonConfiguration).isDataClassEqualTo(
                SingletonConfiguration(SingletonType.BROADCAST))
        }
    }

    @Test
    internal fun `should add minimal specification to the scenario that generates a map`() {
        val scenario = scenario("my-scenario") as StepSpecificationRegistry
        scenario.jackson().jsonToMap {
            classpath("/path/to/my/file")
        }

        assertThat(scenario.rootSteps[0]).isInstanceOf(JsonReaderStepSpecification::class).all {
            prop("targetClass").isEqualTo(Map::class)
            prop(JsonReaderStepSpecification<*>::sourceConfiguration).isDataClassEqualTo(
                    SourceConfiguration(
                            url = this::class.java.getResource("path/to/my/file")
                    ))
            prop(JsonReaderStepSpecification<*>::singletonConfiguration).isDataClassEqualTo(
                SingletonConfiguration(SingletonType.BROADCAST))
        }
    }

    @Test
    internal fun `should configure the singleton as default loop`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().jsonToObject(MyPojo::class) {
            loop()
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(JsonReaderStepSpecification::class).all {
            prop(JsonReaderStepSpecification<*>::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.LOOP)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
            }
        }
    }

    @Test
    internal fun `should configure the singleton as loop with specified timeout`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().jsonToObject(MyPojo::class) {
            loop(Duration.ofDays(3))
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(JsonReaderStepSpecification::class).all {
            prop(JsonReaderStepSpecification<*>::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.LOOP)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ofDays(3))
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
            }
        }
    }

    @Test
    internal fun `should configure the singleton as default broadcast`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().jsonToObject(MyPojo::class) {
            broadcast()

            mapper { mapper->
                mapper.enable(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT)
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(JsonReaderStepSpecification::class).all {
            prop(JsonReaderStepSpecification<*>::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.BROADCAST)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
            }
        }

        val jsonMapper = JsonMapper()
        Assertions.assertFalse(jsonMapper.deserializationConfig.isEnabled(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT))
        (previousStep.nextSteps[0] as JsonReaderStepSpecification<*>).mapperConfiguration(jsonMapper)
        Assertions.assertTrue(jsonMapper.deserializationConfig.isEnabled(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT))
    }

    @Test
    internal fun `should configure the singleton as broadcast with specified timeout and buffer`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().jsonToObject(MyPojo::class) {
            broadcast(123, Duration.ofDays(3))
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(JsonReaderStepSpecification::class).all {
            prop(JsonReaderStepSpecification<*>::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.BROADCAST)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ofDays(3))
                prop(SingletonConfiguration::bufferSize).isEqualTo(123)
            }
        }
    }

    @Test
    internal fun `should configure the singleton as default unicast`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().jsonToObject(MyPojo::class) {
            forwardOnce()
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(JsonReaderStepSpecification::class).all {
            prop(JsonReaderStepSpecification<*>::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
            }
        }
    }

    @Test
    internal fun `should configure the singleton as unicast with specified timeout and buffer`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().jsonToObject(MyPojo::class) {
            forwardOnce(123, Duration.ofDays(3))
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(JsonReaderStepSpecification::class).all {
            prop(JsonReaderStepSpecification<*>::singletonConfiguration).all {
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
