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

package io.qalipsis.plugins.jakarta.deserializer

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.qalipsis.plugins.jakarta.Constants
import jakarta.jms.Message
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import kotlin.math.pow

/**
 * @author Krawist Ngoben
 */
@Testcontainers
internal class JakartaJsonDeserializerIntegrationTest {

    internal data class UserExample(val id: Int, val name: String, val createdAt: Instant)

    private lateinit var connectionFactory: ActiveMQConnectionFactory

    @BeforeAll
    fun setupConnection() {
        connectionFactory = ActiveMQConnectionFactory(
            "tcp://localhost:${container.getMappedPort(61616)}",
            Constants.CONTAINER_USER_NAME,
            Constants.CONTAINER_PASSWORD
        )
    }

    @Test
    fun `should deserialize json`() {
        val deserializer = JakartaJsonDeserializer(UserExample::class)

        val user = UserExample(1, "mike", Instant.now())
        val jsonObject = mapper.writeValueAsString(user)

        val result = deserializer.deserialize(createMessage(jsonObject))

        assertThat(result).all {
            prop(UserExample::name).isEqualTo("mike")
            prop(UserExample::id).isEqualTo(1)
            prop(UserExample::createdAt).isEqualTo(user.createdAt)
        }
    }

    @Test
    fun `should fail when deserializing json with the default object mapper`() {
        val deserializer = JakartaJsonDeserializer(UserExample::class)

        val instantString = "2021-03-04T12:17:47.720Z"
        val userString = """{"id":1, "name":"bob", "createdAt":"$instantString", "email":"bob@mail.com"}"""
            .trimMargin()

        assertThrows<UnrecognizedPropertyException> {
            deserializer.deserialize(createMessage(userString))
        }
    }

    @Test
    fun `should deserialize json with custom mapper configuration`() {
        val deserializer = JakartaJsonDeserializer(UserExample::class) {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }

        val instantString = "2021-03-04T12:17:47.720Z"
        val userString = """{"id":2, "name":"bob", "createdAt":"$instantString", "email":"bob@mail.com"}"""
            .trimMargin()

        val result = deserializer.deserialize(createMessage(userString))

        assertThat(result).all {
            prop(UserExample::name).isEqualTo("bob")
            prop(UserExample::id).isEqualTo(2)
            prop(UserExample::createdAt).isEqualTo(Instant.parse(instantString))
        }
    }

    private fun createMessage(messageText: String): Message {
        return connectionFactory.createConnection().createSession().createTextMessage(messageText)
    }


    companion object {
        private val mapper = JsonMapper().registerModule(JavaTimeModule()).registerModule(
            KotlinModule.Builder()
                .withReflectionCacheSize(512)
                .configure(KotlinFeature.NullToEmptyCollection, false)
                .configure(KotlinFeature.NullToEmptyMap, false)
                .configure(KotlinFeature.NullIsSameAsDefault, false)
                .configure(KotlinFeature.SingletonSupport, false)
                .configure(KotlinFeature.StrictNullChecks, false)
                .build()
        )

        @Container
        @JvmStatic
        val container = GenericContainer<Nothing>(Constants.DOCKER_IMAGE).apply {
            withExposedPorts(61616)
            withCreateContainerCmdModifier {
                it.hostConfig!!.withMemory(256 * 1024.0.pow(2).toLong()).withCpuCount(1)
            }
            withEnv(Constants.CONTAINER_USER_NAME_ENV_KEY, Constants.CONTAINER_USER_NAME)
            withEnv(Constants.CONTAINER_PASSWORD_ENV_KEY, Constants.CONTAINER_PASSWORD)
        }
    }

}
