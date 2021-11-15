package io.qalipsis.plugins.jackson.xml

import assertk.all
import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.dataformat.xml.XmlMapper
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
import io.qalipsis.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.qalipsis.plugins.jackson.JacksonDatasourceIterativeReader
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.InputStreamReader
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempFile

/**
 * @author Maxim Golokhov
 */
@ExperimentalPathApi
@Suppress("UNCHECKED_CAST")
internal class XmlReaderStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<XmlReaderStepSpecificationConverter>() {

    lateinit var spiedConverter: XmlReaderStepSpecificationConverter

    @BeforeEach
    internal fun setUp() {
        spiedConverter = spyk(converter, recordPrivateCalls = true)
    }

    @Test
    override fun `should support expected spec`() {
        // when+then
        Assertions.assertTrue(converter.support(relaxedMockk<XmlReaderStepSpecification<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name`() = runBlockingTest {
        // given
        val spec = XmlReaderStepSpecification(TestPojo::class)
        spec.apply {
            name = "my-step"
            file(createTempFile().toFile().absolutePath)
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val reader: DatasourceIterativeReader<TestPojo> = relaxedMockk { }
        every { spiedConverter["createReader"](refEq(spec)) } returns reader

        // when
        spiedConverter.convert<Unit, TestPojo>(
            creationContext as StepCreationContext<XmlReaderStepSpecification<*>>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).all {
                isInstanceOf(IterativeDatasourceStep::class)
                prop("id").isEqualTo("my-step")
                prop("reader").isSameAs(reader)
                typedProp<Any>("processor").isInstanceOf(NoopDatasourceObjectProcessor::class)
                typedProp<Any>("converter").isInstanceOf(DatasourceRecordObjectConverter::class)
            }
        }
    }

    @Test
    internal fun `should convert spec without name`() = runBlockingTest {
        // given
        val spec = XmlReaderStepSpecification(TestPojo::class)
        spec.apply {
            file(createTempFile().toFile().absolutePath)
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val reader: DatasourceIterativeReader<TestPojo> = relaxedMockk { }
        every { spiedConverter["createReader"](refEq(spec)) } returns reader

        // when
        spiedConverter.convert<Unit, TestPojo>(
            creationContext as StepCreationContext<XmlReaderStepSpecification<*>>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).all {
                isInstanceOf(IterativeDatasourceStep::class)
                prop("id").isNotNull()
                prop("reader").isSameAs(reader)
                typedProp<Any>("processor").isInstanceOf(NoopDatasourceObjectProcessor::class)
                typedProp<Any>("converter").isInstanceOf(DatasourceRecordObjectConverter::class)
            }
        }
    }

    @Test
    internal fun `should generate an error when creating a mapper without source`() = runBlockingTest {
        // given
        val spec = XmlReaderStepSpecification(TestPojo::class)
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        assertThrows<InvalidSpecificationException> {
            converter.convert<Unit, TestPojo>(
                creationContext as StepCreationContext<XmlReaderStepSpecification<*>>
            )
        }
    }

    @Test
    internal fun `should create jackson XML mapper`() {
        // given
        val spec = XmlReaderStepSpecification(TestPojo::class)
        spec.apply {
            mapper {
                it.enable(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT)
            }
        }

        // when
        val mapper = converter.invokeInvisible<XmlMapper>("createMapper",spec)

        // then
        assertThat(mapper).all {
            transform { it.registeredModuleIds }.containsAll(
                "com.fasterxml.jackson.module.kotlin.KotlinModule",
                "com.fasterxml.jackson.datatype.jdk8.Jdk8Module",
                "com.fasterxml.jackson.datatype.jsr310.JavaTimeModule",
                "io.micronaut.jackson.modules.BeanIntrospectionModule"
            )
        }
        Assertions.assertTrue(
            mapper.deserializationConfig.isEnabled(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT))
    }

    @Test
    internal fun `should create reader when a source is specified`() {
        // given
        val xmlFile = createTempFile().toFile()
        xmlFile.writeText("""dummy string""")

        val spec = XmlReaderStepSpecification(TestPojo::class)
        spec.apply {
            file(xmlFile.absolutePath)
        }

        val mapper: XmlMapper = relaxedMockk { }
        every { spiedConverter["createMapper"](any<XmlReaderStepSpecification<*>>()) } returns mapper
        val objectReader: ObjectReader = relaxedMockk { }
        every { mapper.readerFor(any<Class<*>>()) } returns objectReader

        // when
        val reader = spiedConverter.invokeInvisible<DatasourceIterativeReader<*>>("createReader",spec)

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
