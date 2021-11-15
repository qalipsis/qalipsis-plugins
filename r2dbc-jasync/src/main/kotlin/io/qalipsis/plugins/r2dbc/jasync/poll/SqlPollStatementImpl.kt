package io.qalipsis.plugins.r2dbc.jasync.poll

import io.qalipsis.plugins.r2dbc.jasync.dialect.Dialect
import org.apache.calcite.sql.*
import org.apache.calcite.sql.parser.SqlParser

internal class SqlPollStatementImpl(
        private val dialect: Dialect,
        private var sql: String,
        private val initialParameters: List<Any?>,
        private val tieBreakerName: String,
        strictTieBreaker: Boolean
) : SqlPollStatement {

    private val initialSql = sql

    private val sqlNode =
        SqlParser.create(sql, SqlParser.config().withQuoting(dialect.quotingConfig)).parseQuery() as SqlOrderBy

    private val tieBreakerOperator: String

    init {
        val order = getTieBreakerSortingOrder()
        tieBreakerOperator = if (order.kind == SqlKind.DESCENDING) {
            if (strictTieBreaker) {
                "<"
            } else {
                "<="
            }
        } else if (strictTieBreaker) {
            ">"
        } else {
            ">="
        }
    }

    /**
     * Validates that the tie-breaker is the first sorting column and returns its sorting order.
     */
    private fun getTieBreakerSortingOrder(): SqlNode {
        val firstOrder = sqlNode.orderList.firstOrNull()
        val firstOrderIdentifier = when (firstOrder) {
            is SqlIdentifier -> {
                firstOrder
            }
            is SqlBasicCall -> {
                firstOrder.operand(0) as SqlIdentifier
            }
            null -> {
                throw IllegalArgumentException("The tie-breaker should be set as the first sorting column")
            }
            else -> {
                throw IllegalArgumentException("Unsupported type for ordering statement: ${firstOrder::class}")
            }
        }

        val order = if (firstOrderIdentifier.simple.lowercase() == tieBreakerName.lowercase()
        ) {
            firstOrder
        } else {
            null
        }
        requireNotNull(order) {
            "The tie-breaker should be set as the first sorting column"
        }
        return order
    }

    /**
     * Backed property that can be reset to null.
     */
    private var actualTieBreaker: Any? = null

    override var tieBreaker: Any?
        set(value) {
            if (value != null) {
                if (tieBreaker == null) {
                    insertTieBreakerClause()
                }
                actualTieBreaker = value
            }
        }
        get() = actualTieBreaker

    private fun insertTieBreakerClause() {
        val stringBuilder = StringBuilder(sql)
        // Time to complete the query.
        val selectNode = sqlNode.query as SqlSelect

        var insertPosition: Int
        if (selectNode.hasWhere()) {
            val where = selectNode.where as SqlBasicCall
            insertPosition = where.parserPosition.endColumnNum
            if (where.operator.kind == SqlKind.OR) {
                // Surround the current where with brackets.
                stringBuilder.insert(where.parserPosition.endColumnNum, ") AND ")
                stringBuilder.insert(where.parserPosition.columnNum - 1, "(")
                insertPosition += 7
            } else {
                stringBuilder.insert(where.parserPosition.endColumnNum, " AND ")
                insertPosition += 5
            }
        } else {
            stringBuilder.insert(selectNode.parserPosition.endColumnNum, " WHERE ")
            insertPosition = selectNode.parserPosition.endColumnNum + 7
        }
        stringBuilder.insert(insertPosition, "${dialect.quote(tieBreakerName)} $tieBreakerOperator ?")
        sql = stringBuilder.toString()
    }

    override val query: String
        get() = sql

    override val parameters: List<Any?>
        get() = if (tieBreaker != null) {
            initialParameters + listOf(tieBreaker)
        } else initialParameters

    override fun reset() {
        actualTieBreaker = null
        sql = initialSql
    }
}
