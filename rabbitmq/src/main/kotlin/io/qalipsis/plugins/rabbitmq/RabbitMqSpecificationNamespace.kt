package io.qalipsis.plugins.rabbitmq

import io.qalipsis.api.scenario.ScenarioSpecification
import io.qalipsis.api.steps.AbstractPluginStepWrapper
import io.qalipsis.api.steps.AbstractScenarioSpecificationWrapper
import io.qalipsis.api.steps.StepSpecification


/**
 * Step wrapper to append to all steps before using a step from the RabbitMQ plugin.
 *
 * @author Gabriel Moraes
 */
interface RabbitMqStepSpecification<INPUT, OUTPUT, SELF : StepSpecification<INPUT, OUTPUT, SELF>> :
    StepSpecification<INPUT, OUTPUT, SELF>

/**
 * Step wrapper to append to all steps before using a step from the RabbitMQ plugin.
 *
 * @author Gabriel Moraes
 */
class RabbitMqSpecificationImpl<INPUT, OUTPUT>(wrappedStepSpec: StepSpecification<INPUT, OUTPUT, *>) :
    AbstractPluginStepWrapper<INPUT, OUTPUT>(wrappedStepSpec),
    RabbitMqStepSpecification<INPUT, OUTPUT, AbstractPluginStepWrapper<INPUT, OUTPUT>>

fun <INPUT, OUTPUT> StepSpecification<INPUT, OUTPUT, *>.rabbitmq(): RabbitMqStepSpecification<INPUT, OUTPUT, *> =
    RabbitMqSpecificationImpl(this)

/**
 * Scenario wrapper to enter the namespace for the RabbitMQ step specifications.
 *
 * You can learn more about RabbitMQ on [the official website](https://www.rabbitmq.com/#getstarted).
 *
 * @author Gabriel Moraes
 */
class RabbitMqScenarioSpecification(scenario: ScenarioSpecification) : AbstractScenarioSpecificationWrapper(scenario)

fun ScenarioSpecification.rabbitmq() = RabbitMqScenarioSpecification(this)
