package io.qalipsis.plugins.influxdb

import io.qalipsis.api.scenario.ScenarioSpecification
import io.qalipsis.api.steps.AbstractPluginStepWrapper
import io.qalipsis.api.steps.AbstractScenarioSpecificationWrapper
import io.qalipsis.api.steps.StepSpecification


/**
 * Step wrapper to append to all steps before using a step from the Influxdb plugin.
 *
 * @author Eric Jessé
 */
interface InfluxdbStepSpecification<INPUT, OUTPUT, SELF : StepSpecification<INPUT, OUTPUT, SELF>> :
    StepSpecification<INPUT, OUTPUT, SELF>

/**
 * Step wrapper to append to all steps before using a step from the Influxdb plugin.
 *
 * @author Eric Jessé
 */
class InfluxdbSpecificationImpl<INPUT, OUTPUT>(wrappedStepSpec: StepSpecification<INPUT, OUTPUT, *>) :
    AbstractPluginStepWrapper<INPUT, OUTPUT>(wrappedStepSpec),
    InfluxdbStepSpecification<INPUT, OUTPUT, AbstractPluginStepWrapper<INPUT, OUTPUT>>

fun <INPUT, OUTPUT> StepSpecification<INPUT, OUTPUT, *>.influxdb(): InfluxdbStepSpecification<INPUT, OUTPUT, *> =
    InfluxdbSpecificationImpl(this)

/**
 * Scenario wrapper to append to a scenario before using a step from the Influxdb plugin.
 *
 * @author Eric Jessé
 */
class InfluxdbScenarioSpecification(scenario: ScenarioSpecification) :
    AbstractScenarioSpecificationWrapper(scenario)

fun ScenarioSpecification.influxdb() = InfluxdbScenarioSpecification(this)
