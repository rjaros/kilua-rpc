/*
 * Copyright (c) 2024 Robert Jaros
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

import com.google.inject.Injector
import io.vertx.core.Vertx
import io.vertx.core.http.ServerWebSocket
import io.vertx.core.json.Json
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.dispatcher
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.modules.SerializersModule
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.reflect.KClass

public typealias RequestHandler = (RoutingContext) -> Unit
public typealias WebsocketHandler = (Injector, ServerWebSocket) -> Unit
public typealias SseHandler = RequestHandler

/**
 * Fullstack service manager for Vert.x.
 */
public actual open class RpcServiceManager<out T : Any> actual constructor(private val serviceClass: KClass<T>) :
    RpcServiceMgr<T>,
    RpcServiceBinder<T, RequestHandler, WebsocketHandler, SseHandler>() {

    public companion object {
        public val LOG: Logger = LoggerFactory.getLogger(RpcServiceManager::class.java.name)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun <RET> createRequestHandler(
        method: HttpMethod,
        function: suspend T.(params: List<String?>) -> RET,
        numberOfParams: Int,
        serializerFactory: () -> KSerializer<RET>
    ): RequestHandler {
        val serializer by lazy { serializerFactory() }
        return { ctx ->
            val jsonRpcRequest = if (method == HttpMethod.GET) {
                val parameters = (0..<numberOfParams).map {
                    ctx.request().getParam("p$it")?.let {
                        URLDecoder.decode(it, StandardCharsets.UTF_8)
                    }
                }
                JsonRpcRequest(0, "", parameters)
            } else {
                ctx.body().asJsonObject().mapTo(JsonRpcRequest::class.java)
            }

            val injector = ctx.get<Injector>(RPC_INJECTOR_KEY)
            val service = injector.getInstance(serviceClass.java)
            applicationScope.launch(ctx.vertx().dispatcher()) {
                val response = try {
                    val result = function.invoke(service, jsonRpcRequest.params)
                    JsonRpcResponse(
                        id = jsonRpcRequest.id,
                        result = deSerializer.serializeNullableToString(result, serializer)
                    )
                } catch (e: IllegalParameterCountException) {
                    JsonRpcResponse(id = jsonRpcRequest.id, error = "Invalid parameters")
                } catch (e: Exception) {
                    if (e !is ServiceException && e !is AbstractServiceException) LOG.error(e.message, e)
                    val exceptionJson = if (e is AbstractServiceException) {
                        RpcSerialization.getJson().encodeToString(e)
                    } else null
                    JsonRpcResponse(
                        id = jsonRpcRequest.id, error = e.message ?: "Error",
                        exceptionType = e.javaClass.canonicalName,
                        exceptionJson = exceptionJson
                    )
                }
                ctx.response().putHeader("Content-Type", "application/json").end(Json.encode(response))
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
        return { injector, ws ->
            val incoming = Channel<String>()
            val outgoing = Channel<String>()
            val service = injector.getInstance(serviceClass.java)
            val vertx = injector.getInstance(Vertx::class.java)
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
            response.setChunked(true);
            response.putHeader("Content-Type", "text/event-stream");
            response.putHeader("Connection", "keep-alive");
            response.putHeader("Cache-Control", "no-cache");
            val channel = Channel<String>()
            val injector = ctx.get<Injector>(RPC_INJECTOR_KEY)
            val service = injector.getInstance(serviceClass.java)
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

/**
 * A function to generate routes based on definitions from the service manager.
 */
@Suppress("unused")
public fun <T : Any> Vertx.applyRoutes(
    router: Router, serviceManager: RpcServiceManager<T>, serializersModules: List<SerializersModule>? = null
) {
    serviceManager.deSerializer = kotlinxObjectDeSerializer(serializersModules)
    serviceManager.routeMapRegistry.asSequence().forEach { (method, path, handler) ->
        try {
            io.vertx.core.http.HttpMethod.valueOf(method.name)
        } catch (e: IllegalArgumentException) {
            null
        }?.let {
            router.route(it, path).handler(handler)
        }
    }
    serviceManager.sseRequests.asSequence().forEach { (path, handler) ->
        router.route(path).handler(handler)
    }
}
