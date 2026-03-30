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

package io.qalipsis.plugins.jakarta.consumer

import io.qalipsis.api.logging.LoggerHelper.logger
import jakarta.jms.Message
import jakarta.jms.MessageListener
import kotlinx.coroutines.channels.Channel

/**
 * Implementation of [MessageListener] to asynchronous send received [Message]s to [channel].
 *
 * @author Krawist Ngoben
 */
internal class JakartaChannelForwarder(private val channel: Channel<Message>) : MessageListener {

    override fun onMessage(message: Message) {
        log.trace { "Received message from ${message.jmsDestination}" }
        channel.trySend(message).getOrThrow()
    }

    private companion object {
        val log = logger()
    }
}

