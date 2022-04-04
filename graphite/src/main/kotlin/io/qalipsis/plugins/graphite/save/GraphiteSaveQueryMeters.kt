package io.qalipsis.plugins.graphite.save

import java.time.Duration

/**
 * Meters of the performed query.
 *
 * @property savedMessages count of saved messages
 * @property timeToResult time to until the confirmation of the response (successful or failed)
 *
 * @author Palina Bril
 */
data class GraphiteSaveQueryMeters(
    val savedMessages: Int,
    val timeToResult: Duration
)
