/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.plugins.r2dbc.jasync.poll

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import com.github.jasync.sql.db.general.ArrayRowData
import io.qalipsis.plugins.r2dbc.jasync.dialect.DialectConfigurations
import org.junit.jupiter.api.Test

/**
 *
 * @author Eric Jessé
 */
internal class SqlPollStatementImplTest {

    @Test
    internal fun `should add the tie-breaker clause when there is no where clause for PgSQL`() {
        val sqlPollStatement = SqlPollStatementImpl(
                DialectConfigurations.POSTGRESQL,
                """SELECT "timestamp", device, eventname FROM events ORDER by "timestamp" DESC""",
            listOf("Value 1", true)
        )

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT \"timestamp\", device, eventname FROM events ORDER by \"timestamp\" DESC")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true))
        }

        // when
        sqlPollStatement.saveTiebreaker(ArrayRowData(1, mapOf("timestamp" to 0), arrayOf(12)))

        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                "SELECT \"timestamp\", device, eventname FROM events WHERE \"timestamp\" >= ? ORDER by \"timestamp\" DESC"
            )
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true, 12))
        }

        // when
        sqlPollStatement.saveTiebreaker(ArrayRowData(1, mapOf("timestamp" to 0), arrayOf(20)))

        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                "SELECT \"timestamp\", device, eventname FROM events WHERE \"timestamp\" >= ? ORDER by \"timestamp\" DESC"
            )
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
            listOf("Value 1", true)
        )

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT \"timestamp\", device, eventname FROM events WHERE (device <> ? AND enabled = ?) OR isTool = ? ORDER by \"timestamp\" ")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true))
        }

        // when
        sqlPollStatement.saveTiebreaker(ArrayRowData(1, mapOf("timestamp" to 0), arrayOf(12)))

        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT \"timestamp\", device, eventname FROM events WHERE ((device <> ? AND enabled = ?) OR isTool = ?) AND \"timestamp\" >= ? ORDER by \"timestamp\" ")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true, 12))
        }

        // when
        sqlPollStatement.saveTiebreaker(ArrayRowData(1, mapOf("timestamp" to 0), arrayOf(20)))

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
            listOf("Value 1", true)
        )

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT `timestamp`, device, eventname FROM events WHERE device <> ? AND enabled = ? ORDER by `timestamp` ")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true))
        }

        // when
        sqlPollStatement.saveTiebreaker(ArrayRowData(1, mapOf("timestamp" to 0), arrayOf(12)))

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                "SELECT `timestamp`, device, eventname FROM events WHERE device <> ? AND enabled = ? AND `timestamp` >= ? ORDER by `timestamp` "
            )
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true, 12))
        }

        // when
        sqlPollStatement.saveTiebreaker(ArrayRowData(1, mapOf("timestamp" to 0), arrayOf(20)))

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                "SELECT `timestamp`, device, eventname FROM events WHERE device <> ? AND enabled = ? AND `timestamp` >= ? ORDER by `timestamp` "
            )
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
            listOf("Value 1", true)
        )

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                    "SELECT `timestamp`, devices.name, eventname FROM events INNER JOIN devices ON events.device = devices.id WHERE devices.name <> ? AND devices.enabled = ? ORDER by `timestamp` DESC")
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true))
        }

        // when
        sqlPollStatement.saveTiebreaker(ArrayRowData(1, mapOf("timestamp" to 0), arrayOf(12)))

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                "SELECT `timestamp`, devices.name, eventname FROM events INNER JOIN devices ON events.device = devices.id WHERE devices.name <> ? AND devices.enabled = ? AND `timestamp` >= ? ORDER by `timestamp` DESC"
            )
            prop(SqlPollStatementImpl::parameters).isEqualTo(listOf("Value 1", true, 12))
        }

        // when
        sqlPollStatement.saveTiebreaker(ArrayRowData(1, mapOf("timestamp" to 0), arrayOf(20)))

        // then
        assertThat(sqlPollStatement).all {
            prop(SqlPollStatementImpl::query).isEqualTo(
                "SELECT `timestamp`, devices.name, eventname FROM events INNER JOIN devices ON events.device = devices.id WHERE devices.name <> ? AND devices.enabled = ? AND `timestamp` >= ? ORDER by `timestamp` DESC"
            )
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