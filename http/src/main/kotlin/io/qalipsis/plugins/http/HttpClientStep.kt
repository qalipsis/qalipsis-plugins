package io.qalipsis.plugins.http

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.plugins.http.client.MonitoringResponseConsumer
import io.qalipsis.plugins.http.client.RequestMonitoringInterceptor
import io.qalipsis.plugins.http.connectionProvider.ConnectionProvider
import io.qalipsis.plugins.http.connectionProvider.DedicatedConnectionManager
import io.qalipsis.plugins.http.monitoring.HttpMonitoringCollector
import io.qalipsis.plugins.http.request.HttpRequest
import io.qalipsis.plugins.http.request.HttpRequestBuilder
import io.qalipsis.plugins.http.request.HttpRequestBuilderImpl
import io.qalipsis.plugins.http.request.InternalHttpRequest
import io.qalipsis.plugins.http.response.HttpResponse
import io.qalipsis.plugins.http.response.ResponseConverter
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import org.apache.hc.client5.http.async.HttpAsyncClient
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
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
    private val requestFactory: suspend HttpRequestBuilder.(StepContext<*, *>, IN) -> HttpRequest<*>,
    private val clientConfiguration: HttpClientConfiguration,
    private val connectionProvider: ConnectionProvider,
    private val responseConverter: ResponseConverter<OUT>,
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: CampaignMeterRegistry?,
) : AbstractStep<IN, HttpResult<IN, OUT>>(id, retryPolicy) {

    override suspend fun start(context: StepStartStopContext) {
        connectionProvider.init(context)
    }

    override suspend fun execute(context: StepContext<IN, HttpResult<IN, OUT>>) {
        val input = context.receive()
        val monitoring = HttpMonitoringCollector(context, eventsLogger, meterRegistry)
        val httpClient = connectionProvider.acquire(context)
        try {
            monitoring.recordConnecting()
            val requestProducer = buildAsyncRequestProducer(context, input)
            val httpContext = HttpClientContext.create()
            connectionProvider.prepare(context, httpContext)
            val outcome = doExecute(httpClient, requestProducer, httpContext, monitoring)
            val result = monitoring.toResult(input, outcome.response, outcome.failure)
            if (result.isFailure) {
                throw HttpRequestException(result)
            }
            context.send(result)
        } finally {
            connectionProvider.release(context, httpClient)
        }
    }

    /**
     * Sends the request and suspends until the exchange completes.
     *
     * The callback of the client only completes a deferred from the I/O thread of the client: the metrics and the
     * conversion of the response are performed by the coroutine of the step after it resumes, which avoids an
     * additional dispatch per request and keeps the I/O threads free for the network.
     */
    private suspend fun doExecute(
        httpClient: HttpAsyncClient,
        requestProducer: AsyncRequestProducer,
        httpContext: HttpClientContext,
        monitoring: HttpMonitoringCollector,
    ): RequestOutcome<OUT> {
        val exchange = CompletableDeferred<Result<SimpleHttpResponse>>()
        val startNanos = System.nanoTime()
        val future = httpClient.execute(
            requestProducer, MonitoringResponseConsumer.create(), null, httpContext, ExchangeCallback(exchange)
        )
        val exchangeResult = try {
            exchange.await()
        } catch (e: CancellationException) {
            future?.cancel(true)
            throw e
        }
        if (httpContext.getAttribute(DedicatedConnectionManager.RECONNECTION_ATTR) == true) {
            monitoring.recordReconnection()
        }
        return exchangeResult.fold(
            onSuccess = { response -> convertResponse(monitoring, httpContext, startNanos, response) },
            onFailure = { exception ->
                recordFailureMetrics(monitoring, httpContext, startNanos, exception as Exception)
                RequestOutcome(null, exception)
            }
        )
    }

    private fun convertResponse(
        monitoring: HttpMonitoringCollector,
        httpContext: HttpClientContext,
        startNanos: Long,
        response: SimpleHttpResponse,
    ): RequestOutcome<OUT> = try {
        recordSuccessMetrics(monitoring, httpContext, startNanos, response)
        RequestOutcome(responseConverter.convert(response, httpContext), null)
    } catch (e: Exception) {
        log.error(e) { "Error while processing the HTTP response: ${e.message}" }
        RequestOutcome(null, e)
    }

    private fun recordSuccessMetrics(
        monitoring: HttpMonitoringCollector,
        httpContext: HttpClientContext,
        startNanos: Long,
        response: SimpleHttpResponse,
    ) {
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
        monitoring: HttpMonitoringCollector,
        httpContext: HttpClientContext,
        startNanos: Long,
        exception: Exception,
    ) {
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

    private data class RequestOutcome<OUT>(val response: HttpResponse<OUT>?, val failure: Throwable?)

    /**
     * Callback of the client, executed by its I/O threads: it only completes the [exchange].
     */
    private class ExchangeCallback(
        private val exchange: CompletableDeferred<Result<SimpleHttpResponse>>,
    ) : FutureCallback<SimpleHttpResponse> {

        override fun completed(response: SimpleHttpResponse) {
            exchange.complete(Result.success(response))
        }

        override fun failed(exception: Exception) {
            exchange.complete(Result.failure(exception))
        }

        override fun cancelled() {
            exchange.complete(Result.failure(CancellationException("The request was cancelled")))
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
