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
