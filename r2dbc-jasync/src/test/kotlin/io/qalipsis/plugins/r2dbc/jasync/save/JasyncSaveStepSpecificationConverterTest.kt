/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.plugins.r2dbc.jasync.save

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.github.jasync.sql.db.ConnectionPoolConfiguration
import io.aerisconsulting.catadioptre.invokeInvisible
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.plugins.r2dbc.jasync.JasyncConnection
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


/**
 * @author Carlos Vieira
 */
@WithMockk
@Suppress("UNCHECKED_CAST")
internal class JasyncSaveStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<JasyncSaveStepSpecificationConverter>() {

    @RelaxedMockK
    lateinit var ioCoroutineDispatcher: CoroutineDispatcher

    @RelaxedMockK
    lateinit var connection: JasyncConnection.() -> Unit

    @Test
    override fun `should not support unexpected spec`() {
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    override fun `should support expected spec`() {
        Assertions.assertTrue(converter.support(relaxedMockk<JasyncSaveStepSpecificationImpl<*>>()))
    }

    @Test
    fun `should build connection configuration`() {
        // given
        val spec = JasyncSaveStepSpecificationImpl<Any>().also {
            it.connection {
                host = "my-server"
                port = 5678
                database = "my-other-database"
                username = "my-other-username"
                password = "my-other-password"
            }
        }
        // when
        val configuration = converter.invokeInvisible<ConnectionPoolConfiguration>("buildConnectionConfiguration", spec)
        // then
        assertThat(configuration).isInstanceOf(ConnectionPoolConfiguration::class).all {
            prop("host").isEqualTo("my-server")
            prop("port").isEqualTo(5678)
            prop("database").isEqualTo("my-other-database")
            prop("username").isEqualTo("my-other-username")
            prop("password").isEqualTo("my-other-password")
        }
    }
}
