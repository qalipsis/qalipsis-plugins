package io.qalipsis.plugins.r2dbc.jasync.search

import com.github.jasync.sql.db.ResultSet
import java.time.Duration

data class ResultSetWrapper(
    val resultSet: ResultSet,
    var timeToResponse: Duration
)
