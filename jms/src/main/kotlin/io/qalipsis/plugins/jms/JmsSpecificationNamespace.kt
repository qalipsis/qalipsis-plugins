package io.qalipsis.plugins.jms

import io.qalipsis.api.scenario.ScenarioSpecification
import io.qalipsis.api.steps.AbstractPluginStepWrapper
import io.qalipsis.api.steps.AbstractScenarioSpecificationWrapper
import io.qalipsis.api.steps.StepSpecification


/**
 * Interface of a JMS step to define it in the appropriate step specifications namespace.
 *
 * @author Alexander Sosnovsky
 */
interface JmsStepSpecification<INPUT, OUTPUT, SELF : StepSpecification<INPUT, OUTPUT, SELF>> :
    StepSpecification<INPUT, OUTPUT, SELF>

/**
 * Step wrapper to enter the namespace for the JMS step specifications.
 *
 * @author Alexander Sosnovsky
 */
class JmsStepSpecificationImpl<INPUT, OUTPUT>(wrappedStepSpec: StepSpecification<INPUT, OUTPUT, *>) :
    AbstractPluginStepWrapper<INPUT, OUTPUT>(wrappedStepSpec),
    JmsStepSpecification<INPUT, OUTPUT, AbstractPluginStepWrapper<INPUT, OUTPUT>>

fun <INPUT, OUTPUT> StepSpecification<INPUT, OUTPUT, *>.jms(): JmsStepSpecification<INPUT, OUTPUT, *> =
    JmsStepSpecificationImpl(this)

/**
 * Scenario wrapper to enter the namespace for the JMS step specifications.
 *
 * @author Alexander Sosnovsky
 */
class JmsScenarioSpecification(scenario: ScenarioSpecification) :
    AbstractScenarioSpecificationWrapper(scenario)

fun ScenarioSpecification.jms() = JmsScenarioSpecification(this)