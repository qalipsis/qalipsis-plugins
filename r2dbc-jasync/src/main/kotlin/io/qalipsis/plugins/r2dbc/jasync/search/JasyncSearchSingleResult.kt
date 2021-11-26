package io.qalipsis.plugins.r2dbc.jasync.search

/**
 * Qalispsis representation of a Jasync Single Search result.
 * @property input are the records that can from the previous step.
 * @property record
 *
 * @author Alex Averyanov
 */
class JasyncSearchSingleResult <I, O> (
    val input: I,
    val record: JasyncSearchRecord<O>
)