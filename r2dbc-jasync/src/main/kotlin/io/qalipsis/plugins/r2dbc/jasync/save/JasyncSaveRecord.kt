package io.qalipsis.plugins.r2dbc.jasync.save

/**
 * Records to be passed to the database.
 *
 * @author Carlos Vieira
 */
data class JasyncSaveRecord(
    val parameters: List<Any>
) {
    constructor(vararg value: Any) : this(value.toList())
}

