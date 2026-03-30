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

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.micronaut.jackson.modules.BeanIntrospectionModule
import io.qalipsis.plugins.jakarta.JakartaDeserializer
import jakarta.jms.BytesMessage
import jakarta.jms.Message
import jakarta.jms.TextMessage
import kotlin.reflect.KClass


/**
 * Implementation of [JakartaDeserializer] used to deserialize the Jakarta Message to [JSON].
 *
 * @author Krawist Ngoben
 */
class JakartaJsonDeserializer<V : Any>(
    private val targetClass: KClass<V>,
    mapperConfiguration: (JsonMapper.() -> Unit)? = null
) : JakartaDeserializer<V> {

    private val mapper = JsonMapper()

    init {
        mapper.registerModule(BeanIntrospectionModule())
        mapper.registerModule(JavaTimeModule())
        mapper.registerModule(
            KotlinModule.Builder()
                .withReflectionCacheSize(512)
                .configure(KotlinFeature.NullToEmptyCollection, false)
                .configure(KotlinFeature.NullToEmptyMap, false)
                .configure(KotlinFeature.NullIsSameAsDefault, false)
                .configure(KotlinFeature.SingletonSupport, false)
                .configure(KotlinFeature.StrictNullChecks, false)
                .build()
        )
        mapper.registerModule(Jdk8Module())

        mapperConfiguration?.let {
            mapper.mapperConfiguration()
        }
    }

    /**
     * Deserializes the [message] using the jackson Json library to the specified class [V].
     */
    override fun deserialize(message: Message): V {
        return when (message) {
            is TextMessage -> mapper.readValue(message.text, targetClass.java)
            is BytesMessage -> {
                message.reset()
                val byteArray = ByteArray(message.bodyLength.toInt())
                message.readBytes(byteArray)
                mapper.readValue(byteArray, targetClass.java)
            }

            else -> throw IllegalArgumentException("The message of type ${message::class} is not supported")
        }
    }
}