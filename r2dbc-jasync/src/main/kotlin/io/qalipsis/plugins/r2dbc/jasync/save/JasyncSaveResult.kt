package io.qalipsis.plugins.r2dbc.jasync.save

/**
 * Wrapper for the result of save operation into a SQL database.
 *
 * @property input are the records that can from the previous step.
 * @property resultIds are the ids created by the database.
 * @property jasyncSaveStepMeters the metrics for the Jasync save operation
 *
 * @author Carlos Vieira
 */
data class JasyncSaveResult<I> (
    val input: I,
    val resultIds: List<Long> = emptyList(),
    val jasyncSaveStepMeters: JasyncQueryMeters
)

