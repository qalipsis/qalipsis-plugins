package io.qalipsis.plugins.r2dbc.jasync.search

/**
 * Qalispsis representation of a Jasync Batch Search result.
 * @property input are the records that can from the previous step.
 * @property records list of the Datasource records.
 * @property meters meters form the search operation.
 *
 * @author Alex Averyanov
 */

class JasyncSearchBatchResults <I, O> (
    val input: I,
    val records: List<JasyncSearchRecord<O>>,
    val meters: JasyncSearchMeters
) : Iterable<JasyncSearchRecord<O>> {

    override fun iterator(): Iterator<JasyncSearchRecord<O>> {
        return records.iterator()
    }
}