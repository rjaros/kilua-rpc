package dev.kilua.rpc

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import io.ktor.http.HttpMethod as KtorHttpMethod

/**
 * HTTP client agent using Ktor client for JVM.
 */
public open class CallAgent(urlPrefix: String = getRpcUrlPrefix()) {

    private val urlPrefix: String =
        if (urlPrefix.isNotEmpty() && !urlPrefix.endsWith("/")) "$urlPrefix/" else urlPrefix

    // The engine is shared by all agents and is never closed, so the thread pool is
    // created only once. The public HttpClient(engine) factory does not manage the
    // engine, therefore closing a client cannot shut the shared engine down.
    private val client: HttpClient = HttpClient(sharedEngine) {
        install(ContentNegotiation) {
            json(RpcSerialization.getJson())
        }
        install(WebSockets)
        install(SSE)
    }

    private var counter = 1

    public suspend fun jsonRpcCall(
        url: String,
        data: List<String?> = listOf(),
        method: HttpMethod = HttpMethod.POST,
        requestFilter: (suspend HttpRequestBuilder.() -> Unit)? = null
    ): String {
        val urlAddr = urlPrefix + url.drop(1)
        val jsonRpcRequest = JsonRpcRequest(counter++, url, data)

        val ktorMethod = when (method) {
            HttpMethod.GET -> KtorHttpMethod.Get
            HttpMethod.POST -> KtorHttpMethod.Post
            HttpMethod.PUT -> KtorHttpMethod.Put
            HttpMethod.DELETE -> KtorHttpMethod.Delete
            HttpMethod.OPTIONS -> KtorHttpMethod.Options
        }

        val responseBody: String = client.request(urlAddr) {
            this.method = ktorMethod
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
        }.body()

        return parseResponse(responseBody)
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

    private fun parseResponse(responseBody: String): String {
        val response = Json.decodeFromString(JsonRpcResponse.serializer(), responseBody)
        return response.requireResult()
    }

    private companion object {
        private val sharedEngine: HttpClientEngine by lazy { CIO.create() }
    }
}
