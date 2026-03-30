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

package io.qalipsis.plugins.jms.consumer

import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.datasource.DatasourceIterativeReader
import kotlinx.coroutines.channels.Channel
import javax.jms.Destination
import javax.jms.Message
import javax.jms.MessageConsumer
import javax.jms.MessageListener
import javax.jms.Queue
import javax.jms.QueueConnection
import javax.jms.Session
import javax.jms.Session.AUTO_ACKNOWLEDGE
import javax.jms.Topic
import javax.jms.TopicConnection

/**
 * Implementation of [DatasourceIterativeReader] to poll messages from JMS topics or queues.
 *
 * @author Alexander Sosnovsky
 */
internal class JmsConsumerIterativeReader(
    private val stepId: StepName,
    private val topics: Collection<String>,
    private val queues: Collection<String>,
    private val topicConnectionFactory: (() -> TopicConnection)?,
    private val queueConnectionFactory: (() -> QueueConnection)?
) : DatasourceIterativeReader<Message> {

    private val channel = Channel<Message>(Channel.UNLIMITED)

    private val messageListener: MessageListener = JmsChannelForwarder(channel)

    private var running = false

    private val consumers = mutableListOf<MessageConsumer>()

    private var topicConnection: TopicConnection? = null

    private var queueConnection: QueueConnection? = null

    override fun start(context: StepStartStopContext) {
        running = true
        topicConnection = topicConnectionFactory?.invoke()
        queueConnection = queueConnectionFactory?.invoke()

        verifyConnections()

        consumers.clear()
        try {
            startConsumer()
        } catch (e: Exception) {
            log.error(e) { "An error occurred in the step $stepId while starting the consumer: ${e.message}" }
            throw e
        }
    }

    private fun startConsumer() {
        createTopicConsumers(topicConnection, topics)
        createQueueConsumers(queueConnection, queues)

        topicConnection?.start()
        queueConnection?.start()
    }

    override fun stop(context: StepStartStopContext) {
        log.debug { "Stopping the JMS consumer for step $stepId" }
        running = false
        consumers.forEach { it.close() }
        consumers.clear()
        topicConnection?.stop()
        queueConnection?.stop()
        log.debug { "JMS consumer for step $stepId was stopped" }
    }

    override suspend fun hasNext(): Boolean {
        return running
    }

    override suspend fun next(): Message {
        return channel.receive()
    }

    private fun createQueueConsumers(queueConnection: QueueConnection?, queues: Collection<String>) {
        queueConnection?.let {
            val queueSession: Session = queueConnection.createSession(false, AUTO_ACKNOWLEDGE)
            queues.forEach { queueName ->
                val queue: Queue = queueSession.createQueue(queueName)
                val queueConsumer = createMessageConsumer(queueSession, queue)
                consumers.add(queueConsumer)
            }
        }
    }

    private fun createTopicConsumers(topicConnection: TopicConnection?, topics: Collection<String>) {
        topicConnection?.let {
            val topicSession: Session = topicConnection.createSession(false, AUTO_ACKNOWLEDGE)
            topics.forEach { topicName ->
                val topic: Topic = topicSession.createTopic(topicName)
                val topicConsumer = createMessageConsumer(topicSession, topic)
                consumers.add(topicConsumer)
            }
        }

    }

    private fun createMessageConsumer(session: Session, destination: Destination): MessageConsumer {
        val consumer: MessageConsumer = session.createConsumer(destination)
        consumer.messageListener = messageListener
        return consumer
    }

    private fun verifyConnections() {
        if ((queueConnection == null && topicConnection == null)
            || (queueConnection != null && topicConnection != null)) {
            throw IllegalArgumentException("Only one of queueConnection or topicConnection should be provided")
        }

        if (queueConnection != null && topicConnection != null) {
            throw IllegalArgumentException("Only one type of connection should be provided, queueConnection or topicConnection")
        }

        if (queueConnection != null && queues.isEmpty()) {
            throw IllegalArgumentException("At least one queue is expected")
        }

        if (topicConnection != null && topics.isEmpty()) {
            throw IllegalArgumentException("At least one topic is expected")
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
