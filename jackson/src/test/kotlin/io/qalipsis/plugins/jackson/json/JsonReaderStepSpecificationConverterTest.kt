/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.plugins.jackson.json

import assertk.all
import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.databind.json.JsonMapper
import io.aerisconsulting.catadioptre.invokeInvisible
import io.mockk.every
import io.mockk.spyk
import io.mockk.verifyOrder
import io.qalipsis.api.exceptions.InvalidSpecificationException
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.datasource.DatasourceIterativeReader
import io.qalipsis.api.steps.datasource.DatasourceRecordObjectConverter
import io.qalipsis.api.steps.datasource.IterativeDatasourceStep
import io.qalipsis.api.steps.datasource.SequentialDatasourceStep
import io.qalipsis.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.qalipsis.plugins.jackson.JacksonDatasourceIterativeReader
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.InputStreamReader
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempFile
import kotlin.reflect.KClass

/**
 * @author Maxim Golokhov
 */
@ExperimentalPathApi
@Suppress("UNCHECKED_CAST")
internal class JsonReaderStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<JsonReaderStepSpecificationConverter>() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    lateinit var spiedConverter: JsonReaderStepSpecificationConverter

    @BeforeEach
    internal fun setUp() {
        spiedConverter = spyk(converter, recordPrivateCalls = true)
    }

    @Test
    override fun `should support expected spec`() {
        // when+then
        Assertions.assertTrue(converter.support(relaxedMockk<JsonReaderStepSpecification<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name`() = testDispatcherProvider.runTest {
        // given
        val spec = JsonReaderStepSpecification(Map::class as KClass<Map<String, *>>)
        spec.apply {
            name = "my-step"
            file(createTempFile().toFile().absolutePath)
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val reader: DatasourceIterativeReader<Map<String, Any?>> = relaxedMockk { }
        every { spiedConverter["createReader"](refEq(spec)) } returns reader

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<JsonReaderStepSpecification<*>>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).all {
                isInstanceOf(SequentialDatasourceStep::class)
                prop("name").isEqualTo("my-step")
                prop("reader").isSameAs(reader)
                typedProp<Any>("processor").isInstanceOf(NoopDatasourceObjectProcessor::class)
                typedProp<Any>("converter").isInstanceOf(DatasourceRecordObjectConverter::class)
            }
        }
    }

    @Test
    internal fun `should convert spec without name`() = testDispatcherProvider.runTest {
        // given
        val spec = JsonReaderStepSpecification(Map::class as KClass<Map<String, *>>)
        spec.apply {
            file(createTempFile().toFile().absolutePath)
            unicast()
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val reader: DatasourceIterativeReader<Map<String, Any?>> = relaxedMockk { }
        every { spiedConverter["createReader"](refEq(spec)) } returns reader

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<JsonReaderStepSpecification<*>>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).all {
                isInstanceOf(IterativeDatasourceStep::class)
                prop("name").isNotNull()
                prop("reader").isSameAs(reader)
                typedProp<Any>("processor").isInstanceOf(NoopDatasourceObjectProcessor::class)
                typedProp<Any>("converter").isInstanceOf(DatasourceRecordObjectConverter::class)
            }
        }
    }

    @Test
    internal fun `should generate an error when creating a mapper without source`() = testDispatcherProvider.runTest {
        // given
        val spec = JsonReaderStepSpecification(Map::class as KClass<Map<String, *>>)
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        assertThrows<InvalidSpecificationException> {
            converter.convert<Unit, Map<String, *>>(
                creationContext as StepCreationContext<JsonReaderStepSpecification<*>>
            )
        }
    }

    @Test
    internal fun `should create jackson JSON mapper`() {
        // given
        val spec = JsonReaderStepSpecification(TestPojo::class)
        spec.apply {
            mapper {
                it.enable(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT)
            }
        }

        // when
        val mapper = converter.invokeInvisible<JsonMapper>("createMapper", spec)

        // then
        assertThat(mapper).all {
            transform { it.registeredModuleIds }.containsAll(
                "com.fasterxml.jackson.module.kotlin.KotlinModule",
                "com.fasterxml.jackson.datatype.jdk8.Jdk8Module",
                "jackson-datatype-jsr310",
                "io.micronaut.jackson.modules.BeanIntrospectionModule"
            )
        }
        Assertions.assertTrue(
            mapper.deserializationConfig.isEnabled(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT)
        )
    }

    @Test
    internal fun `should create reader when a source is specified`() {
        // given
        val jsonFile = createTempFile().toFile()
        jsonFile.writeText("""dummy string""")

        val spec = JsonReaderStepSpecification(TestPojo::class)
        spec.apply {
            file(jsonFile.absolutePath)
        }

        val mapper: JsonMapper = relaxedMockk { }
        every { spiedConverter["createMapper"](any<JsonReaderStepSpecification<*>>()) } returns mapper
        val objectReader: ObjectReader = relaxedMockk { }
        every { mapper.readerFor(any<Class<*>>()) } returns objectReader

        // when
        val reader = spiedConverter.invokeInvisible<DatasourceIterativeReader<*>>("createReader", spec)

        // then
        assertThat(reader).all {
            isInstanceOf(JacksonDatasourceIterativeReader::class)
            typedProp<InputStreamReader>("inputStreamReader").all {
                transform { it.ready() }.isEqualTo(true)
            }
            prop("objectReader").isSameAs(objectReader)
        }
        verifyOrder {
            spiedConverter["createMapper"](refEq(spec))
            mapper.readerFor(refEq(TestPojo::class.java))
        }
    }


    private data class TestPojo(val field1: Int)
}
