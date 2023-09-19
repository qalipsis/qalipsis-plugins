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

