package io.qalipsis.plugins.graphite

import io.qalipsis.api.scenario.ScenarioSpecification
import io.qalipsis.api.steps.AbstractPluginStepWrapper
import io.qalipsis.api.steps.AbstractScenarioSpecificationWrapper
import io.qalipsis.api.steps.StepSpecification


/**
 * Step wrapper to append to all steps before using a step from the Graphite plugin.
 *
 * @author Palina Bril
 */
interface GraphiteStepSpecification<INPUT, OUTPUT, SELF : StepSpecification<INPUT, OUTPUT, SELF>> :
    StepSpecification<INPUT, OUTPUT, SELF>

/**
 * Step wrapper to append to all steps before using a step from the Graphite plugin.
 *
 * @author Palina Bril
 */
internal class GraphiteSpecificationImpl<INPUT, OUTPUT>(wrappedStepSpec: StepSpecification<INPUT, OUTPUT, *>) :
    AbstractPluginStepWrapper<INPUT, OUTPUT>(wrappedStepSpec),
    GraphiteStepSpecification<INPUT, OUTPUT, AbstractPluginStepWrapper<INPUT, OUTPUT>>

fun <INPUT, OUTPUT> StepSpecification<INPUT, OUTPUT, *>.graphite(): GraphiteStepSpecification<INPUT, OUTPUT, *> =
    GraphiteSpecificationImpl(this)

/**
 * Scenario wrapper to append to a scenario before using a step from the Graphite plugin.
 *
 * @author Palina Bril
 */
class GraphiteScenarioSpecification(scenario: ScenarioSpecification) :
    AbstractScenarioSpecificationWrapper(scenario)

fun ScenarioSpecification.graphite() = GraphiteScenarioSpecification(this)
