package io.qalipsis.plugins.jackson

import io.qalipsis.api.scenario.ScenarioSpecification
import io.qalipsis.api.steps.AbstractPluginStepWrapper
import io.qalipsis.api.steps.AbstractScenarioSpecificationWrapper
import io.qalipsis.api.steps.StepSpecification


/**
 * Interface of a Jackson step to define it in the appropriate step specifications namespace.
 *
 * @author Eric Jessé
 */
interface JacksonStepSpecification<INPUT, OUTPUT, SELF : StepSpecification<INPUT, OUTPUT, SELF>> :
    StepSpecification<INPUT, OUTPUT, SELF>

/**
 * Step wrapper to enter the namespace for the Jackson step specifications.
 *
 * @author Eric Jessé
 */
class JacksonStepSpecificationImpl<INPUT, OUTPUT>(wrappedStepSpec: StepSpecification<INPUT, OUTPUT, *>) :
    AbstractPluginStepWrapper<INPUT, OUTPUT>(wrappedStepSpec),
    JacksonStepSpecification<INPUT, OUTPUT, AbstractPluginStepWrapper<INPUT, OUTPUT>>

fun <INPUT, OUTPUT> StepSpecification<INPUT, OUTPUT, *>.jackson(): JacksonStepSpecification<INPUT, OUTPUT, *> =
    JacksonStepSpecificationImpl(this)

/**
 * Scenario wrapper to enter the namespace for the Jackson scenario specifications.
 *
 * @author Eric Jessé
 */
class JacksonScenarioSpecification(scenario: ScenarioSpecification) :
    AbstractScenarioSpecificationWrapper(scenario)

fun ScenarioSpecification.jackson() = JacksonScenarioSpecification(this)
