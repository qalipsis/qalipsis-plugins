package io.qalipsis.plugins.jms.consumer

import kotlinx.coroutines.channels.Channel
import javax.jms.Message
import javax.jms.MessageListener

/**
 * Implementation of [MessageListener] to asynchronous send received [Message]s to [channel].
 *
 * @author Alexander Sosnovsky
 */
internal class JmsChannelForwarder(private val channel: Channel<Message>) : MessageListener {

    override fun onMessage(message: Message) {
        channel.trySend(message).getOrThrow()
    }
}

