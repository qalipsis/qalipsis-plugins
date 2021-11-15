package io.qalipsis.plugins.r2dbc.jasync.poll

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.qalipsis.plugins.r2dbc.jasync.dialect.DialectConfigurations
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 *
 * @author Eric Jessé
 */
internal class SqlPollStatementImplTest {

    @Test
    internal fun `should fail when the tie-breaker is not in the ordering statement`() {
        assertThrows<IllegalArgumentException> {
            SqlPollStatementImpl(
                    DialectConfigurations.POSTGRESQL,
                    """SELECT "timestamp", device, eventname FROM events ORDER by "timestamp" DESC""",
                    listOf("Value 1", true),
                    "otherTieBreaker",
                    false
            )
        }
    }

    @Test
    internal fun `should fail when the tie-breaker is not the first ordering statement`() {
        assertThrows<IllegalArgumentException> {
            SqlPollStatementImpl(
                    DialectConfigurations.POSTGRESQL,
                    """SELECT "timestamp", device, eventname FROM events ORDER by "timestamp" DESC, otherTieBreaker""",
                    listOf("Value 1", true),
                    "otherTieBreaker",
                    false
            )
        }
    }

    @Test
    internal fun `should add the tie-breaker clause when there is no where clause for PgSQL`() {
        val sqlPollStatement = SqlPollStatementImpl(
                DialectConfigurations.POSTGRESQL,
                """SELECT "timestamp", device, eventname FROM events ORDER by "timestamp" DESC""",
                listOf("Value 1", true),
                "timestamp",
                true
        )

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT \"timestamp\", device, eventname FROM events ORDER by \"timestamp\" DESC")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true))
        }

        // when
        sqlPollStatement.tieBreaker = null

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT \"timestamp\", device, eventname FROM events ORDER by \"timestamp\" DESC")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true))
        }

        // when
        sqlPollStatement.tieBreaker = 12

        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT \"timestamp\", device, eventname FROM events WHERE \"timestamp\" < ? ORDER by \"timestamp\" DESC")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true, 12))
        }

        // when
        sqlPollStatement.tieBreaker = 20

        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT \"timestamp\", device, eventname FROM events WHERE \"timestamp\" < ? ORDER by \"timestamp\" DESC")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true, 20))
        }

        // when
        sqlPollStatement.reset()

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT \"timestamp\", device, eventname FROM events ORDER by \"timestamp\" DESC")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true))
        }
    }

    @Test
    internal fun `should add the tie-breaker clause when there is already a where clause with OR operator for PgSQL`() {
        val sqlPollStatement = SqlPollStatementImpl(
                DialectConfigurations.POSTGRESQL,
                """SELECT "timestamp", device, eventname FROM events WHERE (device <> ? AND enabled = ?) OR isTool = ? ORDER by "timestamp" """,
                listOf("Value 1", true),
                "timestamp",
                false
        )

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT \"timestamp\", device, eventname FROM events WHERE (device <> ? AND enabled = ?) OR isTool = ? ORDER by \"timestamp\" ")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true))
        }

        // when
        sqlPollStatement.tieBreaker = null

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT \"timestamp\", device, eventname FROM events WHERE (device <> ? AND enabled = ?) OR isTool = ? ORDER by \"timestamp\" ")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true))
        }

        // when
        sqlPollStatement.tieBreaker = 12

        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT \"timestamp\", device, eventname FROM events WHERE ((device <> ? AND enabled = ?) OR isTool = ?) AND \"timestamp\" >= ? ORDER by \"timestamp\" ")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true, 12))
        }

        // when
        sqlPollStatement.tieBreaker = 20

        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT \"timestamp\", device, eventname FROM events WHERE ((device <> ? AND enabled = ?) OR isTool = ?) AND \"timestamp\" >= ? ORDER by \"timestamp\" ")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true, 20))
        }

        // when
        sqlPollStatement.reset()

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT \"timestamp\", device, eventname FROM events WHERE (device <> ? AND enabled = ?) OR isTool = ? ORDER by \"timestamp\" ")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true))
        }
    }

    @Test
    internal fun `should add the tie-breaker clause when there is already a where clause with AND operator for MySQL`() {
        // given
        val sqlPollStatement = SqlPollStatementImpl(
                DialectConfigurations.MYSQL,
                """SELECT `timestamp`, device, eventname FROM events WHERE device <> ? AND enabled = ? ORDER by `timestamp` """,
                listOf("Value 1", true),
                "timestamp",
                true
        )

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT `timestamp`, device, eventname FROM events WHERE device <> ? AND enabled = ? ORDER by `timestamp` ")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true))
        }

        // when
        sqlPollStatement.tieBreaker = null

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT `timestamp`, device, eventname FROM events WHERE device <> ? AND enabled = ? ORDER by `timestamp` ")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true))
        }

        // when
        sqlPollStatement.tieBreaker = 12

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT `timestamp`, device, eventname FROM events WHERE device <> ? AND enabled = ? AND `timestamp` > ? ORDER by `timestamp` ")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true, 12))
        }

        // when
        sqlPollStatement.tieBreaker = 20

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT `timestamp`, device, eventname FROM events WHERE device <> ? AND enabled = ? AND `timestamp` > ? ORDER by `timestamp` ")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true, 20))
        }

        // when
        sqlPollStatement.reset()

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT `timestamp`, device, eventname FROM events WHERE device <> ? AND enabled = ? ORDER by `timestamp` ")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true))
        }
    }


    @Test
    internal fun `should add the tie-breaker clause when there is a join and a where clause with AND operator for MySQL`() {
        // given
        val sqlPollStatement = SqlPollStatementImpl(
                DialectConfigurations.MYSQL,
                """SELECT `timestamp`, devices.name, eventname FROM events INNER JOIN devices ON events.device = devices.id WHERE devices.name <> ? AND devices.enabled = ? ORDER by `timestamp` DESC""",
                listOf("Value 1", true),
                "timestamp",
                false
        )

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT `timestamp`, devices.name, eventname FROM events INNER JOIN devices ON events.device = devices.id WHERE devices.name <> ? AND devices.enabled = ? ORDER by `timestamp` DESC")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true))
        }

        // when
        sqlPollStatement.tieBreaker = null

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT `timestamp`, devices.name, eventname FROM events INNER JOIN devices ON events.device = devices.id WHERE devices.name <> ? AND devices.enabled = ? ORDER by `timestamp` DESC")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true))
        }

        // when
        sqlPollStatement.tieBreaker = 12

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT `timestamp`, devices.name, eventname FROM events INNER JOIN devices ON events.device = devices.id WHERE devices.name <> ? AND devices.enabled = ? AND `timestamp` <= ? ORDER by `timestamp` DESC")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true, 12))
        }

        // when
        sqlPollStatement.tieBreaker = 20

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT `timestamp`, devices.name, eventname FROM events INNER JOIN devices ON events.device = devices.id WHERE devices.name <> ? AND devices.enabled = ? AND `timestamp` <= ? ORDER by `timestamp` DESC")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true, 20))
        }

        // when
        sqlPollStatement.reset()

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT `timestamp`, devices.name, eventname FROM events INNER JOIN devices ON events.device = devices.id WHERE devices.name <> ? AND devices.enabled = ? ORDER by `timestamp` DESC")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true))
        }
    }
}