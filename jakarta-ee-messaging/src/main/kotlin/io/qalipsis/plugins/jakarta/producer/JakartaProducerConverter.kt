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
