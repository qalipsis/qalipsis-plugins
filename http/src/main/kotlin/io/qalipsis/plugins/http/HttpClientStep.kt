package io.qalipsis.plugins.http

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.api.sync.Slot
import io.qalipsis.plugins.http.client.MonitoringResponseConsumer
import io.qalipsis.plugins.http.client.RequestMonitoringInterceptor
import io.qalipsis.plugins.http.connectionProvider.ConnectionProvider
import io.qalipsis.plugins.http.monitoring.HttpMonitoringCollector
import io.qalipsis.plugins.http.request.HttpRequest
import io.qalipsis.plugins.http.request.HttpRequestBuilder
import io.qalipsis.plugins.http.request.HttpRequestBuilderImpl
import io.qalipsis.plugins.http.request.InternalHttpRequest
import io.qalipsis.plugins.http.response.HttpResponse
import io.qalipsis.plugins.http.response.ResponseConverter
import java.time.Duration
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.apache.hc.client5.http.async.HttpAsyncClient
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.http.nio.AsyncRequestProducer

/**
 * HTTP client step that handles sending of requests and receiving responses.
 *
 * @author Francisca Eze
 */
internal class HttpClientStep<IN, OUT>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    private val ioCoroutineContext: CoroutineContext,
    private val requestFactory: suspend HttpRequestBuilder.(StepContext<*, *>, IN) -> HttpRequest<*>,
    private val clientConfiguration: HttpClientConfiguration,
    private val connectionProvider: ConnectionProvider,
    private val responseConverter: ResponseConverter<OUT>,
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: CampaignMeterRegistry?,
) : AbstractStep<IN, HttpResponse<OUT>>(id, retryPolicy) {

    private val monitoringEnabled = eventsLogger != null || meterRegistry != null

    override suspend fun start(context: StepStartStopContext) {
        connectionProvider.init(context)
    }

    override suspend fun execute(context: StepContext<IN, HttpResponse<OUT>>) {
        val input = context.receive()
        val monitoring = if (monitoringEnabled) HttpMonitoringCollector(context, eventsLogger, meterRegistry) else null
        val httpClient = connectionProvider.acquire(context)
        try {
            monitoring?.recordConnecting()
            val requestProducer = buildAsyncRequestProducer(context, input)
            val response = doExecute(httpClient, requestProducer, monitoring)
            context.send(response)
        } finally {
            connectionProvider.release(context, httpClient)
        }
    }

    private suspend fun doExecute(
        httpClient: HttpAsyncClient,
        requestProducer: AsyncRequestProducer,
        monitoring: HttpMonitoringCollector?,
    ): HttpResponse<OUT> {
        val slot = Slot<Result<HttpResponse<OUT>>>()
        val consumer =
            if (monitoringEnabled) MonitoringResponseConsumer.Companion.create() else SimpleResponseConsumer.create()
        val callbackScope = CoroutineScope(ioCoroutineContext)
        val httpContext = HttpClientContext.create()
        val startNanos = System.nanoTime()

        val callback = object : FutureCallback<SimpleHttpResponse> {
            override fun completed(response: SimpleHttpResponse) {
                callbackScope.launch {
                    try {
                        recordSuccessMetrics(monitoring, httpContext, startNanos, response)
                        val converted = responseConverter.convert(response, httpContext)
                        slot.set(Result.success(converted))
                    } catch (e: Exception) {
                        log.error(e) { "Error in HTTP response callback: ${e.message}" }
                        slot.set(Result.failure(e))
                    }
                }
            }

            override fun failed(exception: Exception) {
                callbackScope.launch {
                    recordFailureMetrics(monitoring, httpContext, startNanos, exception)
                    slot.set(Result.failure(exception))
                }
            }

            override fun cancelled() {
                callbackScope.launch {
                    val exception = CancellationException("The request was cancelled")
                    recordFailureMetrics(monitoring, httpContext, startNanos, exception)
                    slot.set(Result.failure(exception))
                }
            }
        }
        httpClient.execute(requestProducer, consumer, null, httpContext, callback)

        return slot.get().getOrThrow()
    }

    private fun recordSuccessMetrics(
        monitoring: HttpMonitoringCollector?,
        httpContext: HttpClientContext,
        startNanos: Long,
        response: SimpleHttpResponse,
    ) {
        if (monitoring == null) return

        val sentNanos = httpContext.getAttribute(RequestMonitoringInterceptor.Companion.SENT_ATTR) as? Long
        val firstByteNanos = httpContext.getAttribute(MonitoringResponseConsumer.Companion.FIRST_BYTE_ATTR) as? Long
        val lastByteNanos = httpContext.getAttribute(MonitoringResponseConsumer.Companion.LAST_BYTE_ATTR) as? Long
        val sentBytes =
            (httpContext.getAttribute(RequestMonitoringInterceptor.Companion.SENT_BYTES_ATTR) as? Number)?.toLong()
                ?: 0L

        // Connection time: from start to request interceptor (which fires after connection is established).
        if (sentNanos != null) {
            val connectDuration = Duration.ofNanos(sentNanos - startNanos)
            if (httpContext.sslSession != null) {
                monitoring.recordTlsConnected(connectDuration)
            } else {
                monitoring.recordConnected(connectDuration)
            }
        }

        monitoring.recordSentBytes(sentBytes)

        // Time to first byte: from request sent to first response byte (headers arrived).
        if (firstByteNanos != null && sentNanos != null) {
            monitoring.recordTimeToFirstByte(Duration.ofNanos(firstByteNanos - sentNanos))
        }

        // Time to last byte: from start to response fully received.
        val receivedBytes = response.bodyBytes?.size?.toLong() ?: 0L
        if (lastByteNanos != null) {
            monitoring.recordReceivedResponse(Duration.ofNanos(lastByteNanos - startNanos), receivedBytes)
        }
        monitoring.recordReceivedBytes(receivedBytes)
        monitoring.recordHttpStatus(response.code)
    }

    private fun recordFailureMetrics(
        monitoring: HttpMonitoringCollector?,
        httpContext: HttpClientContext,
        startNanos: Long,
        exception: Exception,
    ) {
        if (monitoring == null) return

        val sentNanos = httpContext.getAttribute(RequestMonitoringInterceptor.Companion.SENT_ATTR) as? Long
        val failDuration = Duration.ofNanos(System.nanoTime() - startNanos)

        if (sentNanos != null) {
            // Connection succeeded but the request/response failed.
            val connectDuration = Duration.ofNanos(sentNanos - startNanos)
            if (httpContext.sslSession != null) {
                monitoring.recordTlsConnected(connectDuration)
            } else {
                monitoring.recordConnected(connectDuration)
            }
        } else {
            // Connection itself failed — the request interceptor never fired.
            monitoring.recordConnectionFailure(failDuration, exception)
        }
        monitoring.recordRequestFailure(failDuration, exception)
    }

    override suspend fun stop(context: StepStartStopContext) {
        connectionProvider.shutdown(context)
    }

    // Helper to convert a factory-produced HttpRequest into AsyncRequestProducer at runtime.
    private suspend fun buildAsyncRequestProducer(ctx: StepContext<*, *>, input: IN): AsyncRequestProducer {
        val httpRequest = requestFactory.invoke(HttpRequestBuilderImpl, ctx, input)
        val internal = httpRequest as InternalHttpRequest<*, *>
        return internal.toAsyncRequest(clientConfiguration)
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}