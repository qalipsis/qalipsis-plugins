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

package io.qalipsis.plugins.jms.deserializer

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.micronaut.jackson.modules.BeanIntrospectionModule
import io.qalipsis.plugins.jms.JmsDeserializer
import javax.jms.BytesMessage
import javax.jms.Message
import javax.jms.TextMessage
import kotlin.reflect.KClass


class JmsJsonDeserializer<V : Any>(
    private val targetClass: KClass<V>,
    mapperConfiguration: (JsonMapper.() -> Unit)? = null
) : JmsDeserializer<V> {

    private val mapper = JsonMapper()

    init {
        mapper.registerModule(BeanIntrospectionModule())
        mapper.registerModule(JavaTimeModule())
        mapper.registerModule(KotlinModule.Builder().build())
        mapper.registerModule(Jdk8Module())

        mapperConfiguration?.let {
            mapper.mapperConfiguration()
        }
    }

    /**
     * Deserializes the [message] using the jackson Json library to the specified class [V].
     */
    override fun deserialize(message: Message): V {
        when (message) {
            is TextMessage -> return mapper.readValue(message.text, targetClass.java)
            is BytesMessage -> {
                message.reset()
                val byteArray = ByteArray(message.bodyLength.toInt())
                message.readBytes(byteArray)
                return mapper.readValue(byteArray, targetClass.java)
            }
            else -> throw IllegalArgumentException("The message of type ${message::class} is not supported")
        }
    }
}