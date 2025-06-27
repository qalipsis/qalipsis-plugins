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

package io.qalipsis.plugins.jms.producer

import java.io.Serializable
import javax.jms.BytesMessage
import javax.jms.Message
import javax.jms.ObjectMessage
import javax.jms.Session

/**
 * Converter from a [JmsProducerRecord] to a native JMS [Message].
 *
 * @author Alexander Sosnovsky
 */
internal class JmsProducerConverter {

    fun convert(message: JmsProducerRecord, session: Session) : Message {
        return when (message.messageType) {
            JmsMessageType.TEXT -> session.createTextMessage(message.value as String?)
            JmsMessageType.BYTES -> createByteMessage(message, session)
            JmsMessageType.OBJECT -> createObjectMessage(message, session)
            JmsMessageType.AUTO -> createAutoMessage(message, session)
        }
    }

    private fun createByteMessage(message: JmsProducerRecord, session: Session) : BytesMessage {
        val byteMessage = session.createBytesMessage()
        byteMessage.writeBytes(message.value as ByteArray)
        return byteMessage
    }

    private fun createObjectMessage(message: JmsProducerRecord, session: Session) : ObjectMessage {
        if (message.value !is Serializable) {
            throw IllegalArgumentException("Only serializable types are supported, but ${message.value::class.simpleName} is not")
        }
        return session.createObjectMessage(message.value)
    }

    private fun createAutoMessage(message: JmsProducerRecord, session: Session) : Message {
        return when (message.value) {
            is String -> session.createTextMessage(message.value as String?)
            is ByteArray -> createByteMessage(message, session)
            else -> createObjectMessage(message, session)
        }
    }
}
