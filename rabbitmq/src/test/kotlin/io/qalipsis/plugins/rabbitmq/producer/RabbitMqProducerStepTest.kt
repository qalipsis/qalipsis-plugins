package io.qalipsis.plugins.rabbitmq.producer

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyExactly
import io.qalipsis.test.mockk.verifyOnce
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert
import org.junit.jupiter.api.Test

/**
 * @author rklymenko
 */
@WithMockk
class RabbitMqProducerStepTest {

    @RelaxedMockK
    private lateinit var retryPolicy: RetryPolicy

    @RelaxedMockK
    private lateinit var rabbitMqProducer: RabbitMqProducer

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var context: StepContext<Any, RabbitMqProducerResult>

    private lateinit var rabbitMqProducerStep: RabbitMqProducerStep<Any>

    @RelaxedMockK
    private lateinit var meterRegistry: MeterRegistry

    private val data = listOf(
        RabbitMqProducerRecord(
            exchange = "test-exchange",
            routingKey = "test-routing-key",
            value = "test".toByteArray(),
            props = null
        )
    )

    @Test
    fun `should produce regular message`() = runBlockingTest {

        val stepName = "test-step"

        rabbitMqProducerStep = RabbitMqProducerStep(
            stepId = stepName,
            retryPolicy = retryPolicy,
            rabbitMqProducer = rabbitMqProducer,
            recordFactory = { _, _ -> data },
            eventsLogger = eventsLogger,
            meterRegistry = meterRegistry,
            rabbitMqProducerResult = RabbitMqProducerResult()
        )

        val localContext = StepStartStopContext(campaignId = "1", scenarioId = "1", dagId = "1", stepId = stepName)

        rabbitMqProducerStep.start(localContext)

        verifyOnce {
            meterRegistry.counter("rabbitmq-produce.${stepName}-bytes", any<Iterable<Tag>>())
            meterRegistry.counter("rabbitmq-produce.${stepName}-records", any<Iterable<Tag>>())
            meterRegistry.counter("rabbitmq-produce.${stepName}-failed-bytes", any<Iterable<Tag>>())
            meterRegistry.counter("rabbitmq-produce.${stepName}-failed-records", any<Iterable<Tag>>())
        }

        rabbitMqProducerStep.execute(context)
        verifyOnce {
            async {
                context.receive()
                rabbitMqProducer.execute(data)
            }
        }



        Assert.assertEquals(data, rabbitMqProducerStep.rabbitMqProducerResult.records)

        verifyOnce {
            eventsLogger.info(
                "rabbitmq-produce.${stepName}.success-response-time",
                any<Array<Any>>(),
                timestamp = any(),
                tags = any<Map<String, String>>()
            )
        }

        rabbitMqProducerStep.stop(localContext)

        verifyExactly(4) {
            meterRegistry.remove(any<Counter>())
        }
    }

    @Test
    fun `should produce failed message`() = runBlockingTest {

        val brokenRabbitMqProducer: RabbitMqProducer = relaxedMockk { }
        every { brokenRabbitMqProducer.execute(any()) }.throws(RuntimeException())

        val stepName = "test-step"

        rabbitMqProducerStep = RabbitMqProducerStep(
            stepId = stepName,
            retryPolicy = retryPolicy,
            rabbitMqProducer = brokenRabbitMqProducer,
            recordFactory = { _, _ -> data },
            eventsLogger = eventsLogger,
            meterRegistry = meterRegistry,
            rabbitMqProducerResult = RabbitMqProducerResult()
        )

        val localContext = StepStartStopContext(campaignId = "1", scenarioId = "1", dagId = "1", stepId = stepName)

        rabbitMqProducerStep.start(localContext)

        verifyOnce {
            meterRegistry.counter("rabbitmq-produce.${stepName}-bytes", any<Iterable<Tag>>())
            meterRegistry.counter("rabbitmq-produce.${stepName}-records", any<Iterable<Tag>>())
            meterRegistry.counter("rabbitmq-produce.${stepName}-failed-bytes", any<Iterable<Tag>>())
            meterRegistry.counter("rabbitmq-produce.${stepName}-failed-records", any<Iterable<Tag>>())
        }

        try {
            rabbitMqProducerStep.execute(context)
            Assert.fail()
        } catch (e: RuntimeException) {

        }
        verifyOnce {
            async {
                context.receive()
                brokenRabbitMqProducer.execute(data)
            }
        }



        Assert.assertEquals(data, rabbitMqProducerStep.rabbitMqProducerResult.records)

        verifyOnce {
            eventsLogger.warn(
                "rabbitmq-produce.${stepName}.failure-response-time",
                any<Array<Any>>(),
                timestamp = any(),
                tags = any<Map<String, String>>()
            )
        }

        rabbitMqProducerStep.stop(localContext)

        verifyExactly(4) {
            meterRegistry.remove(any<Counter>())
        }
    }
}