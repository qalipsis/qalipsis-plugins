/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

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
