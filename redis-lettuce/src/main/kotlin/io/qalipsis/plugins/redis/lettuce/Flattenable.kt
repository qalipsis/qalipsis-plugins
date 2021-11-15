package io.qalipsis.plugins.redis.lettuce

import io.qalipsis.api.steps.StepSpecification

/**
 * Interface of a step that provides a list of items by default but can be amended to flatten those lists.
 *
 * @author Gabriel Moraes
 */
interface Flattenable<V, I: Iterable<V>> : StepSpecification<Unit, I, Flattenable<V, I>> {

    /**
     * Returns each record of a batch individually to the next steps.
     */
    fun flatten(): StepSpecification<Unit, V, *>

}
