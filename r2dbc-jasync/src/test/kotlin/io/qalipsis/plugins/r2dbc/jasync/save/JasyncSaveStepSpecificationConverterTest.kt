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
