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

package io.qalipsis.plugins.jackson.xml

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
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
import org.junit.jupiter.api.extension.RegisterExtension


/**
 *
 * While tests in [XmlReaderStepSpecificationConverterTest] verify that the generated Jackson configuration is as
 * expected until the finest detail, [EndToEndXmlStepTest] aims at verifying that the mentioned configuration
 * actually does what we expect from it.
 *
 * @author Maxim Golokhov
 */
@Suppress("UNCHECKED_CAST")
internal class EndToEndXmlStepTest {

    val file = "test.xml"

    val converter = XmlReaderStepSpecificationConverter()

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    @Timeout(10)
    internal fun `should convert XML to POJO`() = testDispatcherProvider.runTest {
        val scenario = scenario("my-scenario") as StepSpecificationRegistry
        scenario.jackson().xmlToObject(PojoForTest::class) {
            classpath(file)
            broadcast()
        }
        val result = executeStep<PojoForTest>(this, scenario.rootSteps.first())

        // then
        assertThat(result).all {
            hasSize(2)
            index(0).all {
                prop(DatasourceRecord<PojoForTest?>::ordinal).isEqualTo(0L)
                prop(DatasourceRecord<PojoForTest?>::value).isNotNull().all {
                    prop(PojoForTest::name).isEqualTo("test-1")
                    prop(PojoForTest::position).isEqualTo(listOf("Founder", "CEO", "Writer"))
                    prop(PojoForTest::age).isEqualTo(33)
                }
            }
            index(1).all {
                prop(DatasourceRecord<PojoForTest?>::ordinal).isEqualTo(1L)
                prop(DatasourceRecord<PojoForTest?>::value).isNotNull().all {
                    prop(PojoForTest::name).isEqualTo("test-2")
                    prop(PojoForTest::position).isEqualTo(listOf("Founder", "CTO"))
                    prop(PojoForTest::age).isEqualTo(35)
                }
            }
        }
    }

    private suspend fun <T : Any> executeStep(
        coroutineScope: CoroutineScope,
        specification: StepSpecification<*, *, *>
    ): List<DatasourceRecord<T?>> {
        val spec = specification as XmlReaderStepSpecification<T>
        val result = mutableListOf<DatasourceRecord<T?>>()
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), spec)
        val stepContext = TestStepContext<Unit, DatasourceRecord<T?>>(
            minionId = "",
            scenarioName = "",
            stepName = ""
        )

        val latch = Latch(true)
        coroutineScope.launch {
            for (outputRecord in stepContext.output as Channel) {
                result.add(outputRecord.value)
            }
            latch.release()
        }

        converter.convert<Unit, DatasourceRecord<T?>>(
            creationContext as StepCreationContext<XmlReaderStepSpecification<*>>
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
        val name: String,
        val age: Int,
        @JacksonXmlElementWrapper(useWrapping = false)
        val position: List<String>
    )
}
