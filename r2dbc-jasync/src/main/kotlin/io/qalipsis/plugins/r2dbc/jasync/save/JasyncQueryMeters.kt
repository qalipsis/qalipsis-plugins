package io.qalipsis.plugins.r2dbc.jasync.save

import java.time.Duration

/**
 * Records the metrics for the Jasync poll operation.
 *
 * @property totalDocuments total number of documents
 * @property timeToResponse the time that takes on a successfully being save a records to database.
 * @property successSavedDocuments number of documents successfully save to the database.
 *
 * @author Alex Averyanov
 */
data class JasyncQueryMeters(
    val totalDocuments: Int = 0,
    val timeToResponse: Duration,
    val successSavedDocuments: Int = 0
)