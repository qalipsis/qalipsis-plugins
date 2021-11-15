package io.qalipsis.plugins.jms

import javax.jms.Message


/**
 * Deserializer from a JMS [Message] to a user-defined type.
 */
interface JmsDeserializer<V> {

    /**
     * Deserializes the [message] to the specified type [V].
     *
     * @param message consumed from JMS.
     * @return [V] the specified type to return after deserialization.
     */
    fun deserialize(message: Message): V
}
