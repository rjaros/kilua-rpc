package dev.kilua.rpc

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.http.HttpMethod as KtorHttpMethod

/**
 * The canonical name reported by the server for a plain [ServiceException].
 */
private const val SERVICE_EXCEPTION_TYPE = "dev.kilua.rpc.ServiceException"

/**
 * HTTP client agent using Ktor client for JVM.
 *
 * @param urlPrefix the base url of the rpc server.
 * @param json the configuration used to decode the json-rpc envelope. It has to be the very same
 *   instance the [dev.kilua.rpc.RpcAgent] uses for payloads, because that is the one carrying the
 *   service exception serializers registered by the generated service manager.
 */
public open class CallAgent(
    urlPrefix: String = getRpcUrlPrefix(),
    private val json: Json = RpcSerialization.getJson()
) {

    private val urlPrefix: String =
        if (urlPrefix.isNotEmpty() && !urlPrefix.endsWith("/")) "$urlPrefix/" else urlPrefix

    // The engine is shared by all agents and is never closed, so the thread pool is
    // created only once. The public HttpClient(engine) factory does not manage the
    // engine, therefore closing a client cannot shut the shared engine down.
    private val client: HttpClient = HttpClient(sharedEngine) {
        install(ContentNegotiation) {
            json(json)
        }
        install(WebSockets)
        install(SSE)
    }

    // The agent is meant to be used from several coroutines at once, so the request id has to
    // be handed out atomically. A plain counter could hand the same id to two concurrent calls.
    private val counter = AtomicInteger(1)

    public suspend fun jsonRpcCall(
        url: String,
        data: List<String?> = listOf(),
        method: HttpMethod = HttpMethod.POST,
        requestFilter: (suspend HttpRequestBuilder.() -> Unit)? = null
    ): String {
        val urlAddr = urlPrefix + url.drop(1)
        val requestId = counter.getAndIncrement()
        val jsonRpcRequest = JsonRpcRequest(requestId, url, data)

        val ktorMethod = when (method) {
            HttpMethod.GET -> KtorHttpMethod.Get
            HttpMethod.POST -> KtorHttpMethod.Post
            HttpMethod.PUT -> KtorHttpMethod.Put
            HttpMethod.DELETE -> KtorHttpMethod.Delete
            HttpMethod.OPTIONS -> KtorHttpMethod.Options
        }

        val response = client.request(urlAddr) {
            this.method = ktorMethod
            // The status is inspected manually below, so that it is mapped to the same exception
            // types as in the web client instead of ktor's ClientRequestException/ServerResponseException.
            expectSuccess = false
            if (method == HttpMethod.GET) {
                data.forEachIndexed { index, s ->
                    if (s != null) this.url.parameters.append("p$index", s)
                }
            } else {
                contentType(ContentType.Application.Json)
                setBody(jsonRpcRequest)
            }
            // the filter runs last so that it has the final say over method, body and parameters
            requestFilter?.invoke(this)
        }

        if (!response.status.isSuccess()) {
            if (response.status == HttpStatusCode.Unauthorized) {
                throw SecurityException(response.status.description)
            }
            throw Exception(response.status.description)
        }
        val contentType = response.contentType()
        if (contentType == null || !contentType.match(ContentType.Application.Json)) {
            throw ContentTypeException("Invalid response content type: $contentType")
        }

        return parseResponse(response.bodyAsText(), requestId, method)
    }

    public suspend fun webSocketConnect(
        url: String,
        block: suspend (ClientWebSocketSession) -> Unit
    ) {
        client.webSocket(getWebSocketUrl(urlPrefix + url.drop(1))) {
            block(this)
        }
    }

    public suspend fun sseConnect(
        url: String,
        onEvent: (String) -> Unit
    ) {
        client.sse(urlPrefix + url.drop(1)) {
            // A single collect already runs until the stream is closed. Wrapping it in
            // `while (true)` would re-enter collect on a closed channel and spin the CPU once
            // the server ends the stream.
            incoming.collect {
                if (it.data != null) {
                    onEvent(it.data!!)
                }
            }
        }
    }

    /**
     * Unwraps a json-rpc envelope, mirroring the web client. An error is turned back into the
     * exception the server actually threw, so that a declared service exception can be caught by
     * its own type on every platform.
     */
    private fun parseResponse(responseBody: String, requestId: Int, method: HttpMethod): String {
        val response = json.decodeFromString(JsonRpcResponse.serializer(), responseBody)
        if (method != HttpMethod.GET && response.id != requestId) {
            throw Exception("Invalid response ID")
        }
        val error = response.error
        if (error != null) {
            if (response.exceptionType == SERVICE_EXCEPTION_TYPE) {
                throw ServiceException(error)
            }
            val exceptionJson = response.exceptionJson
            if (exceptionJson != null) {
                throw json.decodeFromString<AbstractServiceException>(exceptionJson)
            }
            throw Exception(error)
        }
        return response.result ?: throw Exception("Invalid response")
    }

    private companion object {
        private val sharedEngine: HttpClientEngine by lazy { CIO.create() }
    }
}
