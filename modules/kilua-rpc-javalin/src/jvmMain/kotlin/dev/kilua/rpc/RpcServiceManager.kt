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

import io.javalin.Javalin
import io.javalin.http.bodyAsClass
import io.javalin.security.RouteRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.reflect.KClass

/**
 * Fullstack service manager for Javalin.
 */
public actual open class RpcServiceManager<out T : Any> actual constructor(private val serviceClass: KClass<T>) :
    RpcServiceMgr<T>,
    RpcServiceBinder<T, RequestHandler, WebsocketHandler, SseHandler>() {

    public companion object {
        public val LOG: Logger = LoggerFactory.getLogger(RpcServiceManager::class.java.name)
        public const val RPC_WS_INCOMING_KEY: String = "dev.kilua.rpc.ws.incoming.key"
        public const val RPC_WS_OUTGOING_KEY: String = "dev.kilua.rpc.ws.outgoing.key"
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
                    ctx.queryParam("p$it")?.let {
                        URLDecoder.decode(it, StandardCharsets.UTF_8)
                    }
                }
                JsonRpcRequest(0, "", parameters)
            } else {
                ctx.bodyAsClass()
            }
            val javalin = ctx.attribute<Javalin>(RPC_JAVALIN_KEY)!!

            @Suppress("UNCHECKED_CAST")
            val service = ServiceRegistry.services[serviceClass]?.invoke(ctx, javalin, DummyWsContext())?.let {
                it as? T
            } ?: throw IllegalStateException("Service ${serviceClass.simpleName} not found")
            val future = applicationScope.future {
                try {
                    val result = function.invoke(service, jsonRpcRequest.params)
                    JsonRpcResponse(
                        id = jsonRpcRequest.id,
                        result = deSerializer.serializeNullableToString(result, serializer)
                    )
                } catch (_: IllegalParameterCountException) {
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
            }
            ctx.future { future.thenAccept { ctx.json(it) } }
        }
    }

    override fun <REQ, RES> createWebsocketHandler(
        function: suspend T.(ReceiveChannel<REQ>, SendChannel<RES>) -> Unit,
        requestSerializerFactory: () -> KSerializer<REQ>,
        responseSerializerFactory: () -> KSerializer<RES>,
    ): WebsocketHandler {
        val requestSerializer by lazy { requestSerializerFactory() }
        val responseSerializer by lazy { responseSerializerFactory() }
        return { ws ->
            ws.onConnect { ctx ->
                val incoming = Channel<String>()
                val outgoing = Channel<String>()
                ctx.attribute(RPC_WS_INCOMING_KEY, incoming)
                ctx.attribute(RPC_WS_OUTGOING_KEY, outgoing)
                val javalin = ctx.attribute<Javalin>(RPC_JAVALIN_KEY)!!

                @Suppress("UNCHECKED_CAST")
                val service = ServiceRegistry.services[serviceClass]?.invoke(DummyContext(), javalin, ctx)?.let {
                    it as? T
                } ?: throw IllegalStateException("Service ${serviceClass.simpleName} not found")
                applicationScope.launch {
                    coroutineScope {
                        launch {
                            outgoing.consumeEach { ctx.send(it) }
                            ctx.session.close()
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
            ws.onClose { ctx ->
                applicationScope.launch {
                    val outgoing = ctx.attribute<Channel<String>>(RPC_WS_OUTGOING_KEY)!!
                    val incoming = ctx.attribute<Channel<String>>(RPC_WS_INCOMING_KEY)!!
                    outgoing.close()
                    incoming.close()
                }
            }
            ws.onMessage { ctx ->
                applicationScope.launch {
                    val incoming = ctx.attribute<Channel<String>>(RPC_WS_INCOMING_KEY)!!
                    incoming.send(ctx.message())
                }
            }
        }

    }

    override fun <PAR> createSseHandler(
        function: suspend T.(SendChannel<PAR>) -> Unit,
        serializerFactory: () -> KSerializer<PAR>
    ): SseHandler {
        val serializer by lazy { serializerFactory() }
        return { sseClient ->
            val channel = Channel<String>()
            val javalin = sseClient.ctx().attribute<Javalin>(RPC_JAVALIN_KEY)!!

            @Suppress("UNCHECKED_CAST")
            val service =
                ServiceRegistry.services[serviceClass]?.invoke(sseClient.ctx(), javalin, DummyWsContext())?.let {
                    it as? T
                } ?: throw IllegalStateException("Service ${serviceClass.simpleName} not found")
            sseClient.onClose {
                channel.close()
            }
            runBlocking {
                coroutineScope {
                    launch {
                        channel.consumeEach {
                            sseClient.sendEvent(it)
                        }
                        sseClient.close()
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
public fun <T : Any> Javalin.applyRoutes(
    serviceManager: RpcServiceManager<T>,
    roles: Set<RouteRole> = setOf(),
    serializersModules: List<SerializersModule>? = null
) {
    serviceManager.deSerializer = kotlinxObjectDeSerializer(serializersModules)
    serviceManager.routeMapRegistry.asSequence().forEach { (method, path, handler) ->
        when (method) {
            HttpMethod.GET -> get(path, handler, *roles.toTypedArray())
            HttpMethod.POST -> post(path, handler, *roles.toTypedArray())
            HttpMethod.PUT -> put(path, handler, *roles.toTypedArray())
            HttpMethod.DELETE -> delete(path, handler, *roles.toTypedArray())
            HttpMethod.OPTIONS -> options(path, handler, *roles.toTypedArray())
        }
    }
    serviceManager.webSocketRequests.forEach { (path, handler) ->
        ws(path, handler, *roles.toTypedArray())
    }
    serviceManager.sseRequests.forEach { (path, handler) ->
        sse(path, handler, *roles.toTypedArray())
    }
}
