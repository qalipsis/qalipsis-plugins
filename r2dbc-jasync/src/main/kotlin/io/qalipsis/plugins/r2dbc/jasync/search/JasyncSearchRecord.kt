package io.qalipsis.plugins.r2dbc.jasync.search

data class JasyncSearchRecord<O: Any?> (
    val ordinal: Long,
    val value: O
)
