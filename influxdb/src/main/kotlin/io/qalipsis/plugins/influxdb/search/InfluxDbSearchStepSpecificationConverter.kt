package io.qalipsis.plugins.influxdb.search

import com.influxdb.client.InfluxDBClientOptions
import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.supplyIf
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter

/**
 * [StepSpecificationConverter] from [InfluxDbSearchStepSpecificationImpl] to [InfluxDbSearchStep]
 * to use the Search API.
 *
 * @author Palina Bril
 */
@StepConverter
internal class InfluxDbSearchStepSpecificationConverter(
    private val meterRegistry: MeterRegistry,
    private val eventsLogger: EventsLogger
) : StepSpecificationConverter<InfluxDbSearchStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is InfluxDbSearchStepSpecificationImpl
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<InfluxDbSearchStepSpecificationImpl<*>>) {
        val spec = creationContext.stepSpecification
        val stepId = spec.name

        @Suppress("UNCHECKED_CAST")
        val step = InfluxDbSearchStep(
            id = stepId,
            retryPolicy = spec.retryPolicy,
            influxDbQueryClient = InfluxDbQueryClientImpl(
                clientFactory = {
                    InfluxDBClientKotlinFactory.create(
                        InfluxDBClientOptions.builder()
                            .url(spec.connectionConfig.url)
                            .authenticate(
                                spec.connectionConfig.user,
                                spec.connectionConfig.password.toCharArray()
                            )
                            .org(spec.connectionConfig.org)
                            .build()
                    )
                },
                eventsLogger = supplyIf(spec.monitoringConfig.events) { eventsLogger },
                meterRegistry = supplyIf(spec.monitoringConfig.meters) { meterRegistry }
            ),
            queryFactory = spec.searchConfig.query as suspend (ctx: StepContext<*, *>, input: Any?) -> String
        )
        creationContext.createdStep(step)
    }
}
