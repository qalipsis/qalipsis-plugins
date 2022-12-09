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
