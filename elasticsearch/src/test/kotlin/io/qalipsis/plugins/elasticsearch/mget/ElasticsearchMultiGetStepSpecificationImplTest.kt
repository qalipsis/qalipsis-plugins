/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.plugins.elasticsearch.mget

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import com.fasterxml.jackson.databind.json.JsonMapper
import io.aerisconsulting.catadioptre.getProperty
import io.mockk.confirmVerified
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.DummyStepSpecification
import io.qalipsis.plugins.elasticsearch.elasticsearch
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.relaxedMockk
import kotlin.random.Random
import org.elasticsearch.client.RestClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 *
 * @author Eric Jessé
 */
internal class ElasticsearchMultiGetStepSpecificationImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    internal fun `should add minimal specification to the step`() = testDispatcherProvider.runTest {
        val previousStep = DummyStepSpecification()
        previousStep.elasticsearch().mget { }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(ElasticsearchMultiGetStepSpecificationImpl::class).all {
            prop(ElasticsearchMultiGetStepSpecificationImpl<*>::name).isEmpty()
            prop(ElasticsearchMultiGetStepSpecificationImpl<*>::client).isNotNull()
            prop(ElasticsearchMultiGetStepSpecificationImpl<*>::mapper).isNotNull()
            prop(ElasticsearchMultiGetStepSpecificationImpl<*>::queryFactory).isNotNull()
            prop(ElasticsearchMultiGetStepSpecificationImpl<*>::paramsFactory).isNotNull()
            prop(ElasticsearchMultiGetStepSpecificationImpl<*>::convertFullDocument).isFalse()
            prop(ElasticsearchMultiGetStepSpecificationImpl<*>::targetClass).isEqualTo(Map::class)
        }
        val mapperConfigurer = previousStep.nextSteps[0].getProperty<(JsonMapper) -> Unit>("mapper")
        val jsonMapper = relaxedMockk<JsonMapper>()
        mapperConfigurer(jsonMapper)
        confirmVerified(jsonMapper)

        val paramsFactory =
            previousStep.nextSteps[0].getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> Map<String, String?>>(
                "paramsFactory")
        assertThat(paramsFactory(relaxedMockk(), relaxedMockk())).hasSize(0)
    }

    @Test
    internal fun `should add a complete specification to the step`() {
        val clientFactory: () -> RestClient = relaxedMockk()
        val mapperConfigurer: (JsonMapper) -> Unit = relaxedMockk()
        val queryFactory: suspend MultiGetQueryBuilder.(ctx: StepContext<*, *>, input: Int) -> Unit = relaxedMockk()
        val paramsFactory: suspend (ctx: StepContext<*, *>, input: Int) -> Map<String, String?> = relaxedMockk()
        val previousStep = DummyStepSpecification()
        previousStep.elasticsearch().mget {
            name = "my-step"
            client(clientFactory)
            mapper(mapperConfigurer)
            query(queryFactory)
            queryParameters(paramsFactory)
            monitoring {
                all()
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(ElasticsearchMultiGetStepSpecificationImpl::class).all {
            prop(ElasticsearchMultiGetStepSpecificationImpl<*>::name).isEqualTo("my-step")
            prop(ElasticsearchMultiGetStepSpecificationImpl<*>::client).isSameInstanceAs(clientFactory)
            prop(ElasticsearchMultiGetStepSpecificationImpl<*>::mapper).isSameInstanceAs(mapperConfigurer)
            prop(ElasticsearchMultiGetStepSpecificationImpl<*>::queryFactory).isSameInstanceAs(queryFactory)
            prop(ElasticsearchMultiGetStepSpecificationImpl<*>::paramsFactory).isSameInstanceAs(paramsFactory)
            prop(ElasticsearchMultiGetStepSpecificationImpl<*>::convertFullDocument).isFalse()
            prop(ElasticsearchMultiGetStepSpecificationImpl<*>::targetClass).isEqualTo(Map::class)
        }
    }

    @Test
    internal fun `should deserialize the source only to the expected class`() {
        val previousStep = DummyStepSpecification()
        previousStep.elasticsearch().mget {}.deserialize(Random::class)

        assertThat(previousStep.nextSteps[0]).isInstanceOf(ElasticsearchMultiGetStepSpecificationImpl::class).all {
            prop(ElasticsearchMultiGetStepSpecificationImpl<*>::convertFullDocument).isFalse()
            prop(ElasticsearchMultiGetStepSpecificationImpl<*>::targetClass).isEqualTo(Random::class)
        }
    }

    @Test
    internal fun `should deserialize the full document`() {
        val previousStep = DummyStepSpecification()
        previousStep.elasticsearch().mget {}.deserialize(Random::class, true)

        assertThat(previousStep.nextSteps[0]).isInstanceOf(ElasticsearchMultiGetStepSpecificationImpl::class).all {
            prop(ElasticsearchMultiGetStepSpecificationImpl<*>::convertFullDocument).isTrue()
            prop(ElasticsearchMultiGetStepSpecificationImpl<*>::targetClass).isEqualTo(Random::class)
        }
    }
}
