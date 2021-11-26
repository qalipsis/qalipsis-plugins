package io.qalipsis.plugins.r2dbc.jasync.poll

import io.qalipsis.api.steps.datasource.DatasourceRecord

/**
 * Qalispsis representation of a Jasync Poll result.
 *
 * @property records list of the Datasource records.
 * @property meters meters form the poll operation.
 *
 * @author Alex Averyanov
 */

class JasyncPollResults <O> (
    val records: List<DatasourceRecord<O>>,
    val meters: JasyncPollMeters
) : Iterable<DatasourceRecord<O>> {

    override fun iterator(): Iterator<DatasourceRecord<O>> {
        return records.iterator()
    }
}