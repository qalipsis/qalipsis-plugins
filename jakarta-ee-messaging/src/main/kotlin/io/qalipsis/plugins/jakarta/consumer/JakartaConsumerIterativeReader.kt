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

import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.datasource.DatasourceIterativeReader
import jakarta.jms.Destination
import jakarta.jms.Message
import jakarta.jms.MessageConsumer
import jakarta.jms.MessageListener
import jakarta.jms.Queue
import jakarta.jms.QueueConnection
import jakarta.jms.Session
import jakarta.jms.Session.AUTO_ACKNOWLEDGE
import jakarta.jms.Topic
import jakarta.jms.TopicConnection
import kotlinx.coroutines.channels.Channel

/**
 * Implementation of [DatasourceIterativeReader] to poll messages from Jakarta topics or queues.
 *
 * @author Krawist Ngoben
 */
internal class JakartaConsumerIterativeReader(
    private val stepId: StepName,
    private val topics: Collection<String>,
    private val queues: Collection<String>,
    private val topicConnectionFactory: (() -> TopicConnection)?,
    private val queueConnectionFactory: (() -> QueueConnection)?
) : DatasourceIterativeReader<Message> {

    private val channel = Channel<Message>(Channel.UNLIMITED)

    private val messageListener: MessageListener = JakartaChannelForwarder(channel)

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

        require((queueConnection != null && topicConnection == null) || (queueConnection == null && topicConnection != null)){
            "Only one of queueConnection or topicConnection should be provided"
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
