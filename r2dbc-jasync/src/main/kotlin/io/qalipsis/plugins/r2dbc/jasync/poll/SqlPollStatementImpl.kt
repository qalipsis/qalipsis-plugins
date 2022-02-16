package io.qalipsis.plugins.r2dbc.jasync.poll

import com.github.jasync.sql.db.RowData
import io.qalipsis.plugins.r2dbc.jasync.dialect.Dialect
import io.qalipsis.plugins.r2dbc.jasync.dialect.DialectConfigurations
import org.apache.calcite.sql.SqlBasicCall
import org.apache.calcite.sql.SqlIdentifier
import org.apache.calcite.sql.SqlKind
import org.apache.calcite.sql.SqlOrderBy
import org.apache.calcite.sql.SqlSelect
import org.apache.calcite.sql.parser.SqlParser

internal class SqlPollStatementImpl(
    private val dialect: Dialect,
    private var sql: String,
    private val initialParameters: List<Any?>
) : SqlPollStatement {

    private val initialSql = sql

    private val sqlNode =
        SqlParser.create(sql, SqlParser.config().withQuoting(dialect.quotingConfig)).parseQuery() as SqlOrderBy

    private val tieBreakerOperator: String

    private val tieBreakerName: String

    /**
     * Backed property that can be reset to null.
     */
    private var tieBreaker: Any? = null

    init {
        val firstSortingStatement = getTieBreakerSortingOrder()
        // Since the name of the field is uppercased, the actual value is extracted.
        val position =
            (firstSortingStatement.getComponentParserPosition(0).columnNum - 1) until firstSortingStatement.getComponentParserPosition(
                0
            ).endColumnNum
        tieBreakerName = sql.substring(position)
            .trim { it == DialectConfigurations.POSTGRESQL.quotingConfig.string[0] || it == DialectConfigurations.MYSQL.quotingConfig.string[0] }
        tieBreakerOperator = if (firstSortingStatement.kind == SqlKind.DESCENDING) {
            "<="
        } else {
            ">="
        }
    }

    /**
     * Validates that the tie-breaker is the first sorting column and returns its sorting order.
     */
    private fun getTieBreakerSortingOrder(): SqlIdentifier {
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

        return firstOrderIdentifier
    }

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

    override fun saveTiebreaker(record: RowData) {
        val newTiebreaker = record[tieBreakerName]
        if (newTiebreaker != null) {
            if (tieBreaker == null) {
                insertTieBreakerClause()
            }
            tieBreaker = newTiebreaker
        }
    }

    override val query: String
        get() = sql

    override val parameters: List<Any?>
        get() = if (tieBreaker != null) {
            initialParameters + listOf(tieBreaker)
        } else initialParameters

    override fun reset() {
        tieBreaker = null
        sql = initialSql
    }
}
