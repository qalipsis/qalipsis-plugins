package io.qalipsis.plugins.r2dbc.jasync.save

/**
 * Records to be pass to the database.
 *
 * @author Carlos Vieira
 */
data class JasyncSaverRecord(
   val parameters: List<Any>
)

