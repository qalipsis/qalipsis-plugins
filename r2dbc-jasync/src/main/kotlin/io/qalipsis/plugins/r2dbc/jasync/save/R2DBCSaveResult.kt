package io.qalipsis.plugins.r2dbc.jasync.save

import java.time.Duration

/**
 * Wrapper for the result of save operation into a SQL database.
 *
 * @property input are the records that can from the previous step.
 * @property resultIds are the ids created by the database.
 * @property timeToSuccess the time that takes on a successfully being add a records to database.
 * @property timeToFailure the time that takes on a failed being add a records to database.
 * @property successSavedDocuments number of documents successfully add to the database.
 * @property failedSavedDocuments number of documents failed to be add to the database.
 *
 * @author Carlos Vieira
 */
data class R2DBCSaveResult<I> (
    val input: I,
    val resultIds: List<Long> = emptyList(),
    val timeToSuccess: Duration? = null,
    val timeToFailure: Duration? = null,
    val successSavedDocuments: Int? = null,
    val failedSavedDocuments: Int? = null,
)

