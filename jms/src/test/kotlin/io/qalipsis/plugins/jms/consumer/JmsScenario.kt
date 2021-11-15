package io.qalipsis.plugins.jms.consumer

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.rampup.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.blackHole
import io.qalipsis.api.steps.filterNotNull
import io.qalipsis.api.steps.innerJoin
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.onEach
import io.qalipsis.plugins.jms.deserializer.JmsJsonDeserializer
import io.qalipsis.plugins.jms.deserializer.JmsStringDeserializer
import io.qalipsis.plugins.jms.jms
import org.apache.activemq.ActiveMQConnectionFactory
import java.beans.ConstructorProperties
import java.util.concurrent.LinkedBlockingDeque

internal object JmsScenario {

    internal var queueConnection = ActiveMQConnectionFactory().createQueueConnection()

    internal const val minions = 2

    internal val receivedMessages = LinkedBlockingDeque<String>(10)

    @Scenario
    fun consumeRecords() {
        scenario("consumer-jms") {
            minionsCount = minions
            rampUp {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }.start().jms()
            .consume {
                queues("queue-1")
                queueConnection { queueConnection }
            }.deserialize(JmsJsonDeserializer(User::class))
            .innerJoin(
                using = { it.value.record.value.id },
                on = {
                    it.jms()
                        .consume {
                            queues("queue-2")
                            queueConnection { queueConnection }
                        }.deserialize(JmsJsonDeserializer(User::class))
                },
                having = { it.value.record.value.id }
            )
            .filterNotNull()
            .map { joinResult -> joinResult.second.record }
            .onEach {
                receivedMessages.add(it.value.id)
            }
            .blackHole()
    }

    @Scenario
    fun consumeRecordsStringDeserializer() {
        scenario("consumer-jms-string-deserializer") {
            minionsCount = minions
            rampUp {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }.start().jms()
            .consume {
                queues("queue-3")
                queueConnection { queueConnection }
            }.deserialize(JmsStringDeserializer::class)
            .onEach {
                receivedMessages.add(it.record.value)
            }
            .blackHole()
    }

    data class User @ConstructorProperties("id", "name") constructor(val id: String, val name: String)
}
