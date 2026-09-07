/*
 * Copyright (c) 2026 Robert Jaros
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package dev.kilua.rpc

import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.ServerWebSocket
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.dispatcher
import jakarta.enterprise.context.spi.CreationalContext
import jakarta.enterprise.inject.spi.BeanManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.reflect.KClass

/**
 * HTTP request handler type for Quarkus (via Vert.x RoutingContext).
 */
public typealias RequestHandler = (RoutingContext) -> Unit

/**
 * WebSocket handler type for Quarkus.
 */
public typealias WebsocketHandler = (RoutingContext, Vertx, ServerWebSocket) -> Unit

/**
 * SSE handler type for Quarkus.
 */
public typealias SseHandler = (RoutingContext) -> Unit

/**
 * Holder for the CDI BeanManager, initialized at application startup.
 */
public object BeanContextHolder {
    public lateinit var beanManager: BeanManager
}

/**
 * Fullstack service manager for Quarkus.
 */
public actual open class RpcServiceManager<out T : Any> actual constructor(
    private val serviceClass: KClass<T>
) : RpcServiceMgr<T>, RpcServiceBinder<T, RequestHandler, WebsocketHandler, SseHandler>() {

    public companion object {
        public val LOG: Logger = LoggerFactory.getLogger(RpcServiceManager::class.java.name)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Suppress("UNCHECKED_CAST")
    private fun getService(ctx: RoutingContext, ws: ServerWebSocket?): T {
        RoutingContextHolder.routingContext.set(ctx)
        if (ws != null) {
            ServerWebSocketHolder.serverWebSocket.set(ws)
        } else {
            ServerWebSocketHolder.serverWebSocket.remove()
        }
        try {
            val beanManager = BeanContextHolder.beanManager
            val beanList = beanManager.getBeans(serviceClass.java).toList()
            val bean = beanList.firstOrNull() ?: error("No bean found for ${serviceClass.java.name}")
            val creationalContext = beanManager.createCreationalContext(bean) as CreationalContext<T>
            return beanManager.getReference(bean, serviceClass.java, creationalContext) as T
        } finally {
            RoutingContextHolder.routingContext.remove()
            ServerWebSocketHolder.serverWebSocket.remove()
        }
    }

    override fun <RET> createRequestHandler(
        method: HttpMethod,
        function: suspend T.(params: List<String?>) -> RET,
        numberOfParams: Int,
        serializerFactory: () -> KSerializer<RET>
    ): RequestHandler {
        val serializer by lazy { serializerFactory() }
        return { ctx ->
            val request: HttpServerRequest = ctx.request()

            val jsonRpcRequest = if (method == HttpMethod.GET) {
                val parameters: List<String?> = (0..<numberOfParams).map { i ->
                    val value: String? = request.getParam("p$i")
                    if (value != null) {
                        URLDecoder.decode(value, StandardCharsets.UTF_8)
                    } else {
                        null
                    }
                }
                JsonRpcRequest(0, "", parameters)
            } else {
                val body: String = ctx.body().asString() ?: ""
                RpcSerialization.getJson().decodeFromString<JsonRpcRequest>(body)
            }

            val service = getService(ctx, null)

            applicationScope.launch(ctx.vertx().dispatcher()) {
                val response = try {
                    val result = function.invoke(service, jsonRpcRequest.params)
                    deSerializer.serializeNonNull(
                        JsonRpcResponse(
                            id = jsonRpcRequest.id,
                            result = deSerializer.serializeNullableToString(result, serializer)
                        )
                    )
                } catch (_: IllegalParameterCountException) {
                    deSerializer.serializeNonNull(
                        JsonRpcResponse(
                            id = jsonRpcRequest.id,
                            error = "Invalid parameters"
                        )
                    )
                } catch (e: Exception) {
                    if (e !is ServiceException && e !is AbstractServiceException) LOG.error(e.message, e)
                    val exceptionJson = if (e is AbstractServiceException) {
                        RpcSerialization.getJson().encodeToString(e)
                    } else null
                    deSerializer.serializeNonNull(
                        JsonRpcResponse(
                            id = jsonRpcRequest.id,
                            error = e.message ?: "Error",
                            exceptionType = e.javaClass.canonicalName,
                            exceptionJson = exceptionJson
                        )
                    )
                }
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end(response)
            }
        }
    }

    override fun <REQ, RES> createWebsocketHandler(
        function: suspend T.(ReceiveChannel<REQ>, SendChannel<RES>) -> Unit,
        requestSerializerFactory: () -> KSerializer<REQ>,
        responseSerializerFactory: () -> KSerializer<RES>,
    ): WebsocketHandler {
        val requestSerializer by lazy { requestSerializerFactory() }
        val responseSerializer by lazy { responseSerializerFactory() }
        return { ctx, vertx, ws ->
            val incoming = Channel<String>()
            val outgoing = Channel<String>()

            val service = getService(ctx, ws)
            ws.textMessageHandler { message ->
                applicationScope.launch(vertx.dispatcher()) {
                    incoming.send(message)
                }
            }
            ws.closeHandler {
                applicationScope.launch(vertx.dispatcher()) {
                    outgoing.close()
                    incoming.close()
                }
            }
            ws.exceptionHandler {
                applicationScope.launch(vertx.dispatcher()) {
                    outgoing.close()
                    incoming.close()
                }
            }
            applicationScope.launch(vertx.dispatcher()) {
                coroutineScope {
                    launch {
                        for (text in outgoing) {
                            ws.writeTextMessage(text)
                        }
                        if (!ws.isClosed) ws.close()
                    }
                    launch {
                        handleWebsocketConnection(
                            deSerializer = deSerializer,
                            rawIn = incoming,
                            rawOut = outgoing,
                            serializerIn = requestSerializer,
                            serializerOut = responseSerializer,
                            service = service,
                            function = function
                        )
                    }
                }
            }
        }
    }

    override fun <PAR> createSseHandler(
        function: suspend T.(SendChannel<PAR>) -> Unit,
        serializerFactory: () -> KSerializer<PAR>
    ): SseHandler {
        val serializer by lazy { serializerFactory() }
        return { ctx ->
            val response = ctx.response()
            response.setChunked(true)
            response.putHeader("Content-Type", "text/event-stream")
            response.putHeader("Cache-Control", "no-cache")
            response.putHeader("Connection", "keep-alive")
            val channel = Channel<String>()

            val service = getService(ctx, null)
            response.closeHandler {
                channel.close()
            }
            applicationScope.launch(ctx.vertx().dispatcher()) {
                coroutineScope {
                    launch {
                        channel.consumeEach {
                            response.write("data: $it\n\n")
                        }
                        response.end()
                    }
                    launch {
                        handleSseConnection(
                            deSerializer = deSerializer,
                            rawOut = channel,
                            serializerOut = serializer,
                            service = service,
                            function = function
                        )
                    }
                }
            }
        }
    }
}
