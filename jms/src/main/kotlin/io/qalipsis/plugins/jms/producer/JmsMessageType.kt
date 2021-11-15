package io.qalipsis.plugins.jms.producer

/**
 *  Type of [JmsProducerRecord], needed for creation native JMS [Message].
 *
 *  @author Alexander Sosnovsky
 */
enum class JmsMessageType {
    BYTES,
    TEXT,
    OBJECT,
    /**
     * Default value, using for "autocasting" to native JMS [Message] by [JmsProducerConverter].
     */
    AUTO
}
