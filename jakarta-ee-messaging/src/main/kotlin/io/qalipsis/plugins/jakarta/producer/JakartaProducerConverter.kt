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

package io.qalipsis.plugins.jakarta.producer

import jakarta.jms.BytesMessage
import jakarta.jms.Message
import jakarta.jms.ObjectMessage
import jakarta.jms.Session
import java.io.Serializable

/**
 * Converter from a [JakartaProducerRecord] to a native JMS [jakarta.jms.Message].
 *
 * @author Krawist Ngoben
 */
internal class JakartaProducerConverter {

    fun convert(message: JakartaProducerRecord, session: Session): Message {
        return when (message.messageType) {
            JakartaMessageType.TEXT -> session.createTextMessage(message.value as String?)
            JakartaMessageType.BYTES -> createByteMessage(message, session)
            JakartaMessageType.OBJECT -> createObjectMessage(message, session)
            JakartaMessageType.AUTO -> createAutoMessage(message, session)
        }
    }

    private fun createByteMessage(message: JakartaProducerRecord, session: Session): BytesMessage {
        val byteMessage = session.createBytesMessage()
        byteMessage.writeBytes(message.value as ByteArray)
        return byteMessage
    }

    private fun createObjectMessage(message: JakartaProducerRecord, session: Session): ObjectMessage {
        if (message.value !is Serializable) {
            throw IllegalArgumentException("Only serializable types are supported, but ${message.value::class.simpleName} is not")
        }
        return session.createObjectMessage(message.value)
    }

    private fun createAutoMessage(message: JakartaProducerRecord, session: Session): Message {
        return when (message.value) {
            is String -> session.createTextMessage(message.value as String?)
            is ByteArray -> createByteMessage(message, session)
            else -> createObjectMessage(message, session)
        }
    }
}
