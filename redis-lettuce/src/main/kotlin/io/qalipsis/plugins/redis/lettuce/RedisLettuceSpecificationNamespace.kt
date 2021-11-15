package io.qalipsis.plugins.redis.lettuce

import io.qalipsis.api.scenario.ScenarioSpecification
import io.qalipsis.api.steps.AbstractPluginStepWrapper
import io.qalipsis.api.steps.AbstractScenarioSpecificationWrapper
import io.qalipsis.api.steps.StepSpecification


/**
 * Step wrapper to append to all steps before using a step from the Redis-Lettuce plugin.
 *
 * @author Gabriel Moraes
 */
interface RedisLettuceStepSpecification<INPUT, OUTPUT, SELF : StepSpecification<INPUT, OUTPUT, SELF>> :
    StepSpecification<INPUT, OUTPUT, SELF>

/**
 * Step wrapper to append to all steps before using a step from the Redis-Lettuce plugin.
 *
 * @author Gabriel Moraes
 */
internal class RedisLettuceSpecificationImpl<INPUT, OUTPUT>(wrappedStepSpec: StepSpecification<INPUT, OUTPUT, *>) :
    AbstractPluginStepWrapper<INPUT, OUTPUT>(wrappedStepSpec),
    RedisLettuceStepSpecification<INPUT, OUTPUT, AbstractPluginStepWrapper<INPUT, OUTPUT>>

fun <INPUT, OUTPUT> StepSpecification<INPUT, OUTPUT, *>.redisLettuce(): RedisLettuceStepSpecification<INPUT, OUTPUT, *> =
    RedisLettuceSpecificationImpl(this)

/**
 * Scenario wrapper to append to a scenario before using a step from the Redis-Lettuce plugin.
 *
 * @author Gabriel Moraes
 */
class RedisLettuceScenarioSpecification(scenario: ScenarioSpecification) :
    AbstractScenarioSpecificationWrapper(scenario)

fun ScenarioSpecification.redisLettuce() = RedisLettuceScenarioSpecification(this)
