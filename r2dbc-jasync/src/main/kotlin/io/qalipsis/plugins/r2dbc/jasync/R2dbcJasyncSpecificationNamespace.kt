package io.qalipsis.plugins.r2dbc.jasync

import io.qalipsis.api.scenario.ScenarioSpecification
import io.qalipsis.api.steps.AbstractPluginStepWrapper
import io.qalipsis.api.steps.AbstractScenarioSpecificationWrapper
import io.qalipsis.api.steps.StepSpecification


/**
 * Step wrapper to append to all steps before using a step from the R2DBC-Jasync plugin.
 *
 * @author Eric Jessé
 */
interface R2dbcJasyncStepSpecification<INPUT, OUTPUT, SELF : StepSpecification<INPUT, OUTPUT, SELF>> :
    StepSpecification<INPUT, OUTPUT, SELF>

/**
 * Step wrapper to append to all steps before using a step from the R2DBC-Jasync plugin.
 *
 * @author Eric Jessé
 */
class R2DbcJasyncSpecificationImpl<INPUT, OUTPUT>(wrappedStepSpec: StepSpecification<INPUT, OUTPUT, *>) :
    AbstractPluginStepWrapper<INPUT, OUTPUT>(wrappedStepSpec),
    R2dbcJasyncStepSpecification<INPUT, OUTPUT, AbstractPluginStepWrapper<INPUT, OUTPUT>>

fun <INPUT, OUTPUT> StepSpecification<INPUT, OUTPUT, *>.r2dbcJasync(): R2dbcJasyncStepSpecification<INPUT, OUTPUT, *> =
    R2DbcJasyncSpecificationImpl(this)

/**
 * Scenario wrapper to append to a scenario before using a step from the R2DBC-Jasync plugin.
 *
 * @author Eric Jessé
 */
class R2dbcJasyncScenarioSpecification(scenario: ScenarioSpecification) : AbstractScenarioSpecificationWrapper(scenario)

fun ScenarioSpecification.r2dbcJasync() = R2dbcJasyncScenarioSpecification(this)
