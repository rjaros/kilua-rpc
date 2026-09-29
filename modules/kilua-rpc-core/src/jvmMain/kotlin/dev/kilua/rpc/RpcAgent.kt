package dev.kilua.rpc

import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Client side agent for JSON-RPC remote calls using Ktor client for JVM.
 */
@Suppress("LargeClass", "TooManyFunctions")
public open class RpcAgent<T : Any>(
    public val baseUrl: String? = null,
    public val serviceManager: RpcServiceMgr<T>,
    serializersModules: List<SerializersModule>? = null,
    public val requestFilter: (suspend HttpRequestBuilder.() -> Unit)? = null
) {
    @PublishedApi
    internal val logger: Logger = LoggerFactory.getLogger(RpcAgent::class.java)

    // `json` is declared before `callAgent` on purpose: the call agent decodes the json-rpc
    // envelope with it, and that instance has to include the service exception serializers,
    // which the generated service manager registers while the super constructor arguments above
    // are being evaluated.
    public val json: Json = RpcSerialization.getJson(serializersModules)
    public val callAgent: CallAgent = CallAgent(baseUrl ?: getRpcUrlPrefix(), json)

    public inline fun <reified PAR> serialize(value: PAR): String {
        return json.encodeToString(value)
    }

    public suspend inline fun <reified RET : Any, T> call(noinline function: suspend T.() -> RET): RET {
        val (url, method) = serviceManager.requireCall(function)
        val result = callAgent.jsonRpcCall(url, method = method, requestFilter = requestFilter)
        val serializer = json.serializersModule.serializer<RET>()
        return json.decodeFromString(serializer, result)
    }

    @JvmName("callList0")
    public suspend inline fun <reified RET : Any, T> call(
        noinline function: suspend T.() -> List<RET>
    ): List<RET> {
        val (url, method) = serviceManager.requireCall(function)
        val result = callAgent.jsonRpcCall(url, method = method, requestFilter = requestFilter)
        val serializer = json.serializersModule.serializer<List<RET>>()
        return json.decodeFromString(serializer, result)
    }

    public suspend inline fun <reified PAR, reified RET : Any, T> call(
        noinline function: suspend T.(PAR) -> RET, p: PAR
    ): RET {
        val data = serialize(p)
        val (url, method) = serviceManager.requireCall(function)
        val result = callAgent.jsonRpcCall(url, listOf(data), method, requestFilter)
        val serializer = json.serializersModule.serializer<RET>()
        return json.decodeFromString(serializer, result)
    }

    @JvmName("callList1")
    public suspend inline fun <reified PAR, reified RET : Any, T> call(
        noinline function: suspend T.(PAR) -> List<RET>, p: PAR
    ): List<RET> {
        val data = serialize(p)
        val (url, method) = serviceManager.requireCall(function)
        val result = callAgent.jsonRpcCall(url, listOf(data), method, requestFilter)
        val serializer = json.serializersModule.serializer<List<RET>>()
        return json.decodeFromString(serializer, result)
    }

    public suspend inline fun <reified PAR1, reified PAR2, reified RET : Any, T> call(
        noinline function: suspend T.(PAR1, PAR2) -> RET, p1: PAR1, p2: PAR2
    ): RET {
        val data1 = serialize(p1)
        val data2 = serialize(p2)
        val (url, method) = serviceManager.requireCall(function)
        val result = callAgent.jsonRpcCall(url, listOf(data1, data2), method, requestFilter)
        val serializer = json.serializersModule.serializer<RET>()
        return json.decodeFromString(serializer, result)
    }

    @JvmName("callList2")
    public suspend inline fun <reified PAR1, reified PAR2, reified RET : Any, T> call(
        noinline function: suspend T.(PAR1, PAR2) -> List<RET>, p1: PAR1, p2: PAR2
    ): List<RET> {
        val data1 = serialize(p1)
        val data2 = serialize(p2)
        val (url, method) = serviceManager.requireCall(function)
        val result = callAgent.jsonRpcCall(url, listOf(data1, data2), method, requestFilter)
        val serializer = json.serializersModule.serializer<List<RET>>()
        return json.decodeFromString(serializer, result)
    }

    public suspend inline fun <reified PAR1, reified PAR2, reified PAR3, reified RET : Any, T> call(
        noinline function: suspend T.(PAR1, PAR2, PAR3) -> RET, p1: PAR1, p2: PAR2, p3: PAR3
    ): RET {
        val data1 = serialize(p1)
        val data2 = serialize(p2)
        val data3 = serialize(p3)
        val (url, method) = serviceManager.requireCall(function)
        val result = callAgent.jsonRpcCall(url, listOf(data1, data2, data3), method, requestFilter)
        val serializer = json.serializersModule.serializer<RET>()
        return json.decodeFromString(serializer, result)
    }

    @JvmName("callList3")
    public suspend inline fun <reified PAR1, reified PAR2, reified PAR3, reified RET : Any, T> call(
        noinline function: suspend T.(PAR1, PAR2, PAR3) -> List<RET>, p1: PAR1, p2: PAR2, p3: PAR3
    ): List<RET> {
        val data1 = serialize(p1)
        val data2 = serialize(p2)
        val data3 = serialize(p3)
        val (url, method) = serviceManager.requireCall(function)
        val result = callAgent.jsonRpcCall(url, listOf(data1, data2, data3), method, requestFilter)
        val serializer = json.serializersModule.serializer<List<RET>>()
        return json.decodeFromString(serializer, result)
    }

    public suspend inline fun <reified PAR1, reified PAR2, reified PAR3, reified PAR4, reified RET : Any, T> call(
        noinline function: suspend T.(PAR1, PAR2, PAR3, PAR4) -> RET, p1: PAR1, p2: PAR2, p3: PAR3, p4: PAR4
    ): RET {
        val data1 = serialize(p1)
        val data2 = serialize(p2)
        val data3 = serialize(p3)
        val data4 = serialize(p4)
        val (url, method) = serviceManager.requireCall(function)
        val result = callAgent.jsonRpcCall(url, listOf(data1, data2, data3, data4), method, requestFilter)
        val serializer = json.serializersModule.serializer<RET>()
        return json.decodeFromString(serializer, result)
    }

    @JvmName("callList4")
    public suspend inline fun <reified PAR1, reified PAR2, reified PAR3, reified PAR4, reified RET : Any, T> call(
        noinline function: suspend T.(PAR1, PAR2, PAR3, PAR4) -> List<RET>, p1: PAR1, p2: PAR2, p3: PAR3, p4: PAR4
    ): List<RET> {
        val data1 = serialize(p1)
        val data2 = serialize(p2)
        val data3 = serialize(p3)
        val data4 = serialize(p4)
        val (url, method) = serviceManager.requireCall(function)
        val result = callAgent.jsonRpcCall(url, listOf(data1, data2, data3, data4), method, requestFilter)
        val serializer = json.serializersModule.serializer<List<RET>>()
        return json.decodeFromString(serializer, result)
    }

    @Suppress("LongParameterList")
    public suspend inline fun <reified PAR1, reified PAR2, reified PAR3, reified PAR4, reified PAR5,
            reified RET : Any, T> call(
        noinline function: suspend T.(PAR1, PAR2, PAR3, PAR4, PAR5) -> RET,
        p1: PAR1,
        p2: PAR2,
        p3: PAR3,
        p4: PAR4,
        p5: PAR5
    ): RET {
        val data1 = serialize(p1)
        val data2 = serialize(p2)
        val data3 = serialize(p3)
        val data4 = serialize(p4)
        val data5 = serialize(p5)
        val (url, method) = serviceManager.requireCall(function)
        val result =
            callAgent.jsonRpcCall(url, listOf(data1, data2, data3, data4, data5), method, requestFilter)
        val serializer = json.serializersModule.serializer<RET>()
        return json.decodeFromString(serializer, result)
    }

    @JvmName("callList5")
    @Suppress("LongParameterList")
    public suspend inline fun <reified PAR1, reified PAR2, reified PAR3, reified PAR4, reified PAR5,
            reified RET : Any, T> call(
        noinline function: suspend T.(PAR1, PAR2, PAR3, PAR4, PAR5) -> List<RET>,
        p1: PAR1,
        p2: PAR2,
        p3: PAR3,
        p4: PAR4,
        p5: PAR5
    ): List<RET> {
        val data1 = serialize(p1)
        val data2 = serialize(p2)
        val data3 = serialize(p3)
        val data4 = serialize(p4)
        val data5 = serialize(p5)
        val (url, method) = serviceManager.requireCall(function)
        val result =
            callAgent.jsonRpcCall(url, listOf(data1, data2, data3, data4, data5), method, requestFilter)
        val serializer = json.serializersModule.serializer<List<RET>>()
        return json.decodeFromString(serializer, result)
    }

    @Suppress("LongParameterList")
    public suspend inline fun <reified PAR1, reified PAR2, reified PAR3, reified PAR4, reified PAR5, reified PAR6,
            reified RET : Any, T> call(
        noinline function: suspend T.(PAR1, PAR2, PAR3, PAR4, PAR5, PAR6) -> RET,
        p1: PAR1,
        p2: PAR2,
        p3: PAR3,
        p4: PAR4,
        p5: PAR5,
        p6: PAR6
    ): RET {
        val data1 = serialize(p1)
        val data2 = serialize(p2)
        val data3 = serialize(p3)
        val data4 = serialize(p4)
        val data5 = serialize(p5)
        val data6 = serialize(p6)
        val (url, method) = serviceManager.requireCall(function)
        val result =
            callAgent.jsonRpcCall(url, listOf(data1, data2, data3, data4, data5, data6), method, requestFilter)
        val serializer = json.serializersModule.serializer<RET>()
        return json.decodeFromString(serializer, result)
    }

    @JvmName("callList6")
    @Suppress("LongParameterList")
    public suspend inline fun <reified PAR1, reified PAR2, reified PAR3, reified PAR4, reified PAR5, reified PAR6,
            reified RET : Any, T> call(
        noinline function: suspend T.(PAR1, PAR2, PAR3, PAR4, PAR5, PAR6) -> List<RET>,
        p1: PAR1,
        p2: PAR2,
        p3: PAR3,
        p4: PAR4,
        p5: PAR5,
        p6: PAR6
    ): List<RET> {
        val data1 = serialize(p1)
        val data2 = serialize(p2)
        val data3 = serialize(p3)
        val data4 = serialize(p4)
        val data5 = serialize(p5)
        val data6 = serialize(p6)
        val (url, method) = serviceManager.requireCall(function)
        val result =
            callAgent.jsonRpcCall(url, listOf(data1, data2, data3, data4, data5, data6), method, requestFilter)
        val serializer = json.serializersModule.serializer<List<RET>>()
        return json.decodeFromString(serializer, result)
    }

    /**
     * Opens a websocket connection and hands control to [webSocketLoop] for the duration of the
     * call. The [function] reference is kept in the signature because the route is derived from
     * its name via [RpcServiceMgr.requireCall].
     */
    @OptIn(DelicateCoroutinesApi::class)
    public suspend inline fun <reified PAR1 : Any, reified PAR2 : Any> webSocket(
        noinline function: suspend T.(ReceiveChannel<PAR1>, SendChannel<PAR2>) -> Unit,
        noinline handler: suspend (SendChannel<PAR1>, ReceiveChannel<PAR2>) -> Unit
    ) {
        val (url, _) = serviceManager.requireCall(function)
        webSocketCall(
            callAgent, json, logger, url,
            json.serializersModule.serializer<PAR1>(), json.serializersModule.serializer<PAR2>(),
            handler
        )
    }

    /**
     * Websocket variant where the server streams a list of objects for every request. Shares the
     * [webSocketLoop] body with the single object overload; only the response serializer differs.
     */
    @OptIn(DelicateCoroutinesApi::class)
    @JvmName("webSocketList")
    public suspend inline fun <reified PAR1 : Any, reified PAR2 : Any> webSocket(
        noinline function: suspend T.(ReceiveChannel<PAR1>, SendChannel<List<PAR2>>) -> Unit,
        noinline handler: suspend (SendChannel<PAR1>, ReceiveChannel<List<PAR2>>) -> Unit
    ) {
        val (url, _) = serviceManager.requireCall(function)
        webSocketCall(
            callAgent, json, logger, url,
            json.serializersModule.serializer<PAR1>(),
            ListSerializer(json.serializersModule.serializer<PAR2>()),
            handler
        )
    }

    /**
     * Connects, opens the two channels and runs the call. The channels are created here (and not
     * in [webSocketLoop]) so they can be closed again once the session is gone.
     */
    @PublishedApi
    @OptIn(DelicateCoroutinesApi::class)
    internal suspend fun <PAR1 : Any, PAR2 : Any> webSocketCall(
        callAgent: CallAgent,
        json: Json,
        logger: Logger,
        url: String,
        serializerPAR1: KSerializer<PAR1>,
        serializerPAR2: KSerializer<PAR2>,
        handler: suspend (SendChannel<PAR1>, ReceiveChannel<PAR2>) -> Unit
    ) {
        val requestChannel = Channel<PAR1>()
        val responseChannel = Channel<PAR2>()
        try {
            // The session is closed as soon as this block returns, so the pumps have to be awaited
            // here rather than launched into the caller's scope.
            callAgent.webSocketConnect(url) { session ->
                webSocketLoop(
                    session,
                    url,
                    requestChannel,
                    responseChannel,
                    json,
                    serializerPAR1,
                    serializerPAR2,
                    logger,
                    handler
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A streaming call is not expected to report setup failures to the caller: it is a
            // long lived connection, so the error is logged and the call returns normally. This
            // differs from `jsonRpcCall`, which propagates, and is deliberate.
            logger.error("RPC websocket call to $url failed", e)
        } finally {
            if (!requestChannel.isClosedForReceive) requestChannel.close()
            if (!responseChannel.isClosedForSend) responseChannel.close()
        }
    }

    /**
     * Executes defined server-sent events connection
     */
    @OptIn(DelicateCoroutinesApi::class)
    public suspend inline fun <reified PAR : Any> sseConnection(
        noinline function: suspend T.(SendChannel<PAR>) -> Unit,
        noinline handler: suspend (ReceiveChannel<PAR>) -> Unit
    ) {
        val (url, _) = serviceManager.requireCall(function)
        sseConnectionCall(callAgent, json, logger, url, json.serializersModule.serializer<PAR>(), handler)
    }

    /**
     * Server-sent events variant where every event carries a list of objects. Shares the
     * [sseConnectionLoop] body with the single object overload; only the event serializer differs.
     */
    @OptIn(DelicateCoroutinesApi::class)
    @JvmName("sseConnectionList")
    public suspend inline fun <reified PAR : Any> sseConnection(
        noinline function: suspend T.(SendChannel<List<PAR>>) -> Unit,
        noinline handler: suspend (ReceiveChannel<List<PAR>>) -> Unit
    ) {
        val (url, _) = serviceManager.requireCall(function)
        sseConnectionCall(
            callAgent, json, logger, url,
            ListSerializer(json.serializersModule.serializer<PAR>()),
            handler
        )
    }

    @PublishedApi
    @OptIn(DelicateCoroutinesApi::class)
    internal suspend fun <PAR : Any> sseConnectionCall(
        callAgent: CallAgent,
        json: Json,
        logger: Logger,
        url: String,
        serializerPAR: KSerializer<PAR>,
        handler: suspend (ReceiveChannel<PAR>) -> Unit
    ) {
        val channel = Channel<PAR>()
        try {
            sseConnectionLoop(callAgent, json, logger, url, serializerPAR, handler, channel)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // As in `webSocketCall`, a failed connection is logged rather than thrown at the caller.
            logger.error("RPC SSE connection to $url failed", e)
        } finally {
            if (!channel.isClosedForSend) channel.close()
        }
    }
}

/**
 * Runs the three websocket pumps until the first one finishes.
 *
 * The jobs are registered before any of them starts, which is what allows a single [teardown]
 * lambda to cancel all of them without any `lateinit` reference. Lazy start mirrors this shape
 * across the JVM and web targets.
 */
@PublishedApi
@OptIn(DelicateCoroutinesApi::class)
internal suspend fun <PAR1 : Any, PAR2 : Any> webSocketLoop(
    session: ClientWebSocketSession,
    url: String,
    requestChannel: Channel<PAR1>,
    responseChannel: Channel<PAR2>,
    json: Json,
    serializerPAR1: KSerializer<PAR1>,
    serializerPAR2: KSerializer<PAR2>,
    logger: Logger,
    handler: suspend (SendChannel<PAR1>, ReceiveChannel<PAR2>) -> Unit
) {
    coroutineScope {
        val jobs = mutableListOf<Job>()
        val teardown: () -> Unit = {
            jobs.forEach { it.cancel() }
            if (!requestChannel.isClosedForReceive) requestChannel.close()
            if (!responseChannel.isClosedForSend) responseChannel.close()
        }
        val requestJob = launch(start = CoroutineStart.LAZY) {
            try {
                for (par1 in requestChannel) {
                    val param = json.encodeToString(serializerPAR1, par1)
                    val str = RpcSerialization.plain.encodeToString(
                        JsonRpcRequest(0, url, listOf(param))
                    )
                    session.send(Frame.Text(str))
                }
            } finally {
                teardown()
            }
        }
        val responseJob = launch(start = CoroutineStart.LAZY) {
            try {
                for (frame in session.incoming) {
                    if (frame is Frame.Text) {
                        val data = json.decodeFromString<JsonRpcResponse>(frame.readText()).result ?: ""
                        val par2 = json.decodeFromString(serializerPAR2, data)
                        responseChannel.send(par2)
                    }
                }
            } finally {
                teardown()
            }
        }
        val handlerJob = launch(start = CoroutineStart.LAZY) {
            try {
                exceptionHelper(logger) { handler(requestChannel, responseChannel) }
            } finally {
                teardown()
            }
        }
        jobs += listOf(requestJob, responseJob, handlerJob)
        requestJob.start()
        responseJob.start()
        handlerJob.start()
    }
}

/**
 * Runs the server-sent events producer, the pump and the handler until the handler is done.
 *
 * Each job has a distinct ending role, which is what keeps the stream lossless:
 * the producer only marks the end of the stream, the pump hands the last events over and then
 * closes the handler channel, and the handler ends the connection. If the producer tore down
 * instead, a fast server would silently drop events for a slow consumer.
 */
@PublishedApi
@OptIn(DelicateCoroutinesApi::class)
internal suspend fun <PAR : Any> sseConnectionLoop(
    callAgent: CallAgent,
    json: Json,
    logger: Logger,
    url: String,
    serializerPAR: KSerializer<PAR>,
    handler: suspend (ReceiveChannel<PAR>) -> Unit,
    channel: Channel<PAR>
) {
    // The SSE client callback cannot suspend, so events are handed off to an unbounded buffer
    // that a single pump drains into the rendezvous channel. That keeps events ordered and
    // lossless (trySend on an unbounded channel only fails once it is closed) while still
    // applying backpressure to the handler.
    val events = Channel<PAR>(Channel.UNLIMITED)
    coroutineScope {
        val jobs = mutableListOf<Job>()
        val teardown: () -> Unit = {
            jobs.forEach { it.cancel() }
            events.close()
            if (!channel.isClosedForSend) channel.close()
        }
        val producerJob = launch(start = CoroutineStart.LAZY) {
            try {
                callAgent.sseConnect(url) { eventData ->
                    val response = json.decodeFromString<JsonRpcResponse>(eventData)
                    events.trySend(json.decodeFromString(serializerPAR, response.requireResult()))
                }
            } finally {
                // Only signal the end of the stream. Tearing down here would cancel the pump and
                // discard whatever is still buffered, so a fast server would silently drop events
                // for a slow consumer.
                events.close()
            }
        }
        val pumpJob = launch(start = CoroutineStart.LAZY) {
            try {
                for (par in events) channel.send(par)
            } finally {
                // the handler sees the end of the stream as a closed channel
                channel.close()
            }
        }
        val handlerJob = launch(start = CoroutineStart.LAZY) {
            try {
                exceptionHelper(logger) { handler(channel) }
            } finally {
                // the handler is what ends the connection
                teardown()
            }
        }
        jobs += listOf(producerJob, pumpJob, handlerJob)
        producerJob.start()
        pumpJob.start()
        handlerJob.start()
    }
}

/**
 * Runs an RPC handler, logging any failure through the agent logger instead of letting it escape
 * into the caller's coroutine. Cancellation is rethrown so structured concurrency still works.
 */
@PublishedApi
internal suspend fun exceptionHelper(logger: Logger, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.error("RPC handler failed", e)
    }
}
