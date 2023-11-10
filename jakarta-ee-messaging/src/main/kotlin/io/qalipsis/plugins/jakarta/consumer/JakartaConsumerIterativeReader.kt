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
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.datasource.DatasourceIterativeReader
import jakarta.jms.Connection
import jakarta.jms.Message
import jakarta.jms.QueueConnection
import jakarta.jms.Session
import jakarta.jms.TopicConnection
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/**
 * Implementation of [DatasourceIterativeReader] to poll messages from Jakarta topics or queues.
 * See the [official documentation](https://jakarta.ee/specifications/messaging/3.0/jakarta-messaging-spec-3.0.html#receiving-messages-asynchronously).
 *
 * @author Krawist Ngoben
 */
internal class JakartaConsumerIterativeReader(
    private val stepId: StepName,
    private val topics: Collection<String>,
    private val queues: Collection<String>,
    private val topicConnectionFactory: (() -> TopicConnection)?,
    private val queueConnectionFactory: (() -> QueueConnection)?,
    private val sessionFactory: ((Connection) -> Session) = Connection::createSession,
) : DatasourceIterativeReader<Message> {

    private var channel: Channel<Message>? = null

    private var running = false

    private lateinit var consumerLatch: CountDownLatch

    private lateinit var consumersOwningThread: Thread

    override fun start(context: StepStartStopContext) {
        consumerLatch = CountDownLatch(1)

        channel = Channel(Channel.UNLIMITED)
        running = true
        // This future aims at waiting for the consumers to be up and running.
        val creationCompletionFuture = CompletableFuture<Result<Unit>>()
        createAllConsumers(creationCompletionFuture)
        // If an exception was thrown be the consumer creation, it should be thrown to stop the whole process.
        creationCompletionFuture.get().getOrThrow()
    }

    /**
     * According to the [documentation](https://jakarta.ee/specifications/messaging/3.0/jakarta-messaging-spec-3.0.html#classic-api-interfaces),
     * the [Session] should be kept and used in a single thread.
     *
     * So we create the sessions for the topics and queue in a unique thread, kept alive during the whole execution
     * of a campaign.
     *
     * The thread will be blocked until [consumerLatch] reaches 0, then will close the connections,
     * which automatically leads to closing the attached consumers and sessions.
     *
     * When an exception occurs during the initialization process, it is provided as result into [creationCompletionFuture]
     * which can be used by the starting operation.
     *
     * When the initialization process succeeds, [creationCompletionFuture] receives an empty result to let the starting
     * operation go on.
     */
    private fun createAllConsumers(creationCompletionFuture: CompletableFuture<Result<Unit>>) {
        consumersOwningThread = thread(
            start = true,
            isDaemon = false,
            name = "jakarta-ee-consumer-${stepId}-consumers"
        ) {

            val messageListener = JakartaChannelForwarder(channel!!)
            val connections = mutableListOf<Connection>()
            try {
                if (topics.isNotEmpty()) {
                    val connection = requireNotNull(topicConnectionFactory).invoke()
                    connections += connection
                    connection.clientID = "qalipsis-jakarta-ee-messaging-topic-consumer-$stepId"
                    val session = sessionFactory(connection)
                    topics.forEach { topicName ->
                        log.debug { "Creating a consumer for the topic $topicName" }
                        session.createConsumer(session.createTopic(topicName)).also {
                            it.messageListener = messageListener
                        }
                    }
                    connection.start()
                }

                if (queues.isNotEmpty()) {
                    val connection = requireNotNull(queueConnectionFactory).invoke()
                    connections += connection
                    connection.clientID = "qalipsis-jakarta-ee-messaging-queue-consumer-$stepId"
                    val session = sessionFactory(connection)
                    queues.forEach { queueName ->
                        log.debug { "Creating a consumer for the queue $queueName" }
                        session.createConsumer(session.createQueue(queueName)).also {
                            it.messageListener = messageListener
                        }
                    }
                    connection.start()
                }
                creationCompletionFuture.complete(Result.success(Unit))

                // Wait until the step to be stopped before we go on and close everything.
                log.debug { "Waiting for the step to be stopped" }
                consumerLatch.await()
            } catch (e: InterruptedException) {
                // Do nothing.
            } catch (e: Exception) {
                log.error(e) { "An error occurred during the initialization of the consumers: ${e.message}" }
                if (!creationCompletionFuture.isDone) {
                    creationCompletionFuture.complete(Result.failure(e))
                }
            } finally {
                log.debug { "Closing the connections" }
                connections.forEach {
                    tryAndLogOrNull(log) {
                        // Closing the connections will close all the attached sessions and consumers.
                        it.close()
                    }
                }
            }
        }
    }

    override fun stop(context: StepStartStopContext) {
        log.debug { "Stopping the Jakarta consumer for step $stepId" }
        running = false
        consumerLatch.countDown()
        kotlin.runCatching {
            // Normally the thread should have been stopped by the consumerLatch already.
            // This is just for double-security.
            consumersOwningThread.interrupt()
        }

        // Releases the resources.
        channel?.cancel()
        channel = null
        log.debug { "Jakarta consumer for step $stepId was stopped" }
    }

    override suspend fun hasNext(): Boolean {
        return running
    }

    override suspend fun next(): Message {
        return channel!!.receive()
    }


    companion object {

        @JvmStatic
        private val log = logger()
    }
}
