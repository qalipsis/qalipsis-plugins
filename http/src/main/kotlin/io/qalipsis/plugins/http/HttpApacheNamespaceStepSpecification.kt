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

package io.qalipsis.plugins.http

import io.qalipsis.api.scenario.ConfigurableScenarioSpecification
import io.qalipsis.api.scenario.ScenarioSpecification
import io.qalipsis.api.steps.AbstractPluginStepWrapper
import io.qalipsis.api.steps.AbstractScenarioSpecificationWrapper
import io.qalipsis.api.steps.StepSpecification


/**
 * Step wrapper to append to all steps before using a step from the Ktor plugin.
 *
 * @author Eric Jessé
 */
interface HttpApachePluginSpecification<INPUT, OUTPUT, SELF : StepSpecification<INPUT, OUTPUT, SELF>> :
    StepSpecification<INPUT, OUTPUT, SELF>

/**
 * Step wrapper to append to all steps before using a step from the Ktor plugin.
 *
 * @author Eric Jessé
 */
class HttpApachePluginSpecificationImpl<INPUT, OUTPUT>(wrappedStepSpec: StepSpecification<INPUT, OUTPUT, *>) :
    AbstractPluginStepWrapper<INPUT, OUTPUT>(wrappedStepSpec),
    HttpApachePluginSpecification<INPUT, OUTPUT, AbstractPluginStepWrapper<INPUT, OUTPUT>>

fun <INPUT, OUTPUT> StepSpecification<INPUT, OUTPUT, *>.httpApache(): HttpApachePluginSpecification<INPUT, OUTPUT, *> =
    HttpApachePluginSpecificationImpl(this)

/**
 * Scenario wrapper to append to a scenario before using a step from the Ktor plugin.
 *
 * @author Eric Jessé
 */
class HttpApacheScenarioSpecification(internal val scenario: ScenarioSpecification) :
    AbstractScenarioSpecificationWrapper(scenario)

fun ScenarioSpecification.httpApache() = HttpApacheScenarioSpecification(this)

/**
 * Scenario wrapper to enter the namespace for the HTTP Apache step specifications in the [ConfigurableScenarioSpecification].
 *
 * @author Eric Jessé
 */
class HttpApacheConfigurableScenarioSpecification(internal val scenario: ConfigurableScenarioSpecification)

fun ConfigurableScenarioSpecification.httpApache() = HttpApacheConfigurableScenarioSpecification(this)
