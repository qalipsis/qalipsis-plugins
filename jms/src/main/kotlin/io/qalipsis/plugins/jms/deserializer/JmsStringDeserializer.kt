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

package io.qalipsis.plugins.jms.deserializer

import io.qalipsis.plugins.jms.JmsDeserializer
import java.nio.charset.Charset
import javax.jms.BytesMessage
import javax.jms.Message
import javax.jms.TextMessage

/**
 * Implementation of [JmsDeserializer] used to deserialize the JMS Message to [String].
 *
 * @author Alexander Sosnovsky
 */
class JmsStringDeserializer(private val defaultCharset: Charset = Charsets.UTF_8) : JmsDeserializer<String> {

    /**
     * Deserializes the [message] to a [String] object using the defined charset.
     */
    override fun deserialize(message: Message): String {
        when (message) {
            is TextMessage -> return message.text
            is BytesMessage -> {
                val byteArray = ByteArray(message.bodyLength.toInt())
                message.readBytes(byteArray)
                return byteArray.toString(defaultCharset)
            }
            else -> return ""
        }
    }
}
