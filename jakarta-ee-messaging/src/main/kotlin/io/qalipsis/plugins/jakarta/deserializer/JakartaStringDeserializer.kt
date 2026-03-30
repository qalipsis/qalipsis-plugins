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

import io.qalipsis.plugins.jakarta.JakartaDeserializer
import jakarta.jms.BytesMessage
import jakarta.jms.Message
import jakarta.jms.TextMessage
import java.nio.charset.Charset

/**
 * Implementation of [JakartaDeserializer] used to deserialize the Jakarta Message to [String].
 *
 * @author Krawist Ngoben
 */
class JakartaStringDeserializer(private val defaultCharset: Charset = Charsets.UTF_8) : JakartaDeserializer<String> {

    /**
     * Deserializes the [message] to a [String] object using the defined charset.
     */
    override fun deserialize(message: Message): String {
        return when (message) {
            is TextMessage -> message.text
            is BytesMessage -> {
                val byteArray = ByteArray(message.bodyLength.toInt())
                message.readBytes(byteArray)
                byteArray.toString(defaultCharset)
            }

            else -> throw IllegalArgumentException("The message of type ${message::class} is not supported")
        }
    }
}
