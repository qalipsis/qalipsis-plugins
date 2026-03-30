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

package io.qalipsis.plugins.jms

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.qalipsis.plugins.jms.deserializer.JmsJsonDeserializer
import org.apache.activemq.command.ActiveMQTextMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import javax.jms.Message

/**
 * @author Alexander Sosnovsky
 */
internal class JmsJsonDeserializerTest {

    internal data class UserExample(val id: Int, val name: String, val createdAt: Instant)

    companion object {
        private val mapper =
            JsonMapper().registerModule(JavaTimeModule()).registerModule(KotlinModule.Builder().build())
    }

    @Test
    fun `should deserialize json`() {
        val deserializer = JmsJsonDeserializer(UserExample::class)

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
        val deserializer = JmsJsonDeserializer(UserExample::class)

        val instantString = "2021-03-04T12:17:47.720Z"
        val userString = """{"id":1, "name":"bob", "createdAt":"$instantString", "email":"bob@mail.com"}"""
            .trimMargin()

        assertThrows<JsonMappingException> {
            deserializer.deserialize(createMessage(userString))
        }
    }

    @Test
    fun `should deserialize json with custom mapper configuration`() {
        val deserializer = JmsJsonDeserializer(UserExample::class) {
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
        val message = ActiveMQTextMessage()

        message.text = messageText

        return message
    }

}
