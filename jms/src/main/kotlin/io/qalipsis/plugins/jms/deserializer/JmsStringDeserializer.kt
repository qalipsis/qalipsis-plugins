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
