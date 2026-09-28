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

import js.json.parse
import js.objects.unsafeJso
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import web.console.console
import web.events.EventHandler
import web.http.Request
import web.messaging.MessageEvent
import web.sse.EventSource
import kotlin.js.toJsBoolean

/**
 * Client side agent for JSON-RPC remote calls.
 */
@Suppress("LargeClass", "TooManyFunctions")
public open class RpcAgent<T : Any>(
    public val serviceManager: RpcServiceMgr<T>,
    serializersModules: List<SerializersModule>? = null,
    public val requestFilter: (suspend Request.() -> Unit)? = null
) {

    public val callAgent: CallAgent = CallAgent()
    public val json: Json = RpcSerialization.getJson(serializersModules)

    public inline fun <reified PAR> serialize(value: PAR): String {
        return json.encodeToString(value)
    }

    /**
     * Executes defined call to a remote web service.
     */
    public suspend inline fun <reified RET : Any, T> call(noinline function: suspend T.() -> RET): RET {
        val (url, method) = serviceManager.requireCall(function)
        val result = callAgent.jsonRpcCall(url, method = method, requestFilter = requestFilter)
        val serializer = json.serializersModule.serializer<RET>()
        return json.decodeFromString(serializer, result)
    }

    /**
     * Executes defined call to a remote web service.
     */
    public suspend inline fun <reified RET : Any, T> call(
        noinline function: suspend T.() -> List<RET>
    ): List<RET> {
        val (url, method) = serviceManager.requireCall(function)
        val result = callAgent.jsonRpcCall(url, method = method, requestFilter = requestFilter)
        val serializer = json.serializersModule.serializer<List<RET>>()
        return json.decodeFromString(serializer, result)
    }

    /**
     * Executes defined call to a remote web service.
     */
    public suspend inline fun <reified PAR, reified RET : Any, T> call(
        noinline function: suspend T.(PAR) -> RET, p: PAR
    ): RET {
        val data = serialize(p)
        val (url, method) = serviceManager.requireCall(function)
        val result = callAgent.jsonRpcCall(url, listOf(data), method, requestFilter)
        val serializer = json.serializersModule.serializer<RET>()
        return json.decodeFromString(serializer, result)
    }

    /**
     * Executes defined call to a remote web service.
     */
    public suspend inline fun <reified PAR, reified RET : Any, T> call(
        noinline function: suspend T.(PAR) -> List<RET>, p: PAR
    ): List<RET> {
        val data = serialize(p)
        val (url, method) = serviceManager.requireCall(function)
        val result = callAgent.jsonRpcCall(url, listOf(data), method, requestFilter)
        val serializer = json.serializersModule.serializer<List<RET>>()
        return json.decodeFromString(serializer, result)
    }

    /**
     * Executes defined call to a remote web service.
     */
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

    /**
     * Executes defined call to a remote web service.
     */
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

    /**
     * Executes defined call to a remote web service.
     */
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

    /**
     * Executes defined call to a remote web service.
     */
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

    /**
     * Executes defined call to a remote web service.
     */
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

    /**
     * Executes defined call to a remote web service.
     */
    public suspend inline fun <reified PAR1, reified PAR2, reified PAR3, reified PAR4, reified RET : Any, T> call(
        noinline function: suspend T.(PAR1, PAR2, PAR3, PAR4) -> List<RET>,
        p1: PAR1,
        p2: PAR2,
        p3: PAR3,
        p4: PAR4
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

    /**
     * Executes defined call to a remote web service.
     */
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

    /**
     * Executes defined call to a remote web service.
     */
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

    /**
     * Executes defined call to a remote web service.
     */
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
        val result = callAgent.jsonRpcCall(url, listOf(data1, data2, data3, data4, data5, data6), method, requestFilter)
        val serializer = json.serializersModule.serializer<RET>()
        return json.decodeFromString(serializer, result)
    }

    /**
     * Executes defined call to a remote web service.
     */
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
        val result = callAgent.jsonRpcCall(url, listOf(data1, data2, data3, data4, data5, data6), method, requestFilter)
        val serializer = json.serializersModule.serializer<List<RET>>()
        return json.decodeFromString(serializer, result)
    }

    /**
     * Executes defined web socket connection
     */
    public suspend inline fun <reified PAR1 : Any, reified PAR2 : Any> webSocket(
        noinline function: suspend T.(ReceiveChannel<PAR1>, SendChannel<PAR2>) -> Unit,
        noinline handler: suspend (SendChannel<PAR1>, ReceiveChannel<PAR2>) -> Unit
    ) {
        if (!isDom) return
        val urlPrefix = baseUrl ?: getRpcUrlPrefix()
        val (url, _) = serviceManager.requireCall(function)
        webSocketCall(
            urlPrefix, url, json,
            json.serializersModule.serializer<PAR1>(), json.serializersModule.serializer<PAR2>(),
            handler
        )
    }

    /**
     * Executes defined web socket connection returning list objects
     */
    public suspend inline fun <reified PAR1 : Any, reified PAR2 : Any> webSocket(
        noinline function: suspend T.(ReceiveChannel<PAR1>, SendChannel<List<PAR2>>) -> Unit,
        noinline handler: suspend (SendChannel<PAR1>, ReceiveChannel<List<PAR2>>) -> Unit
    ) {
        if (!isDom) return
        val urlPrefix = baseUrl ?: getRpcUrlPrefix()
        val (url, _) = serviceManager.requireCall(function)
        webSocketCall(
            urlPrefix, url, json,
            json.serializersModule.serializer<PAR1>(),
            ListSerializer(json.serializersModule.serializer<PAR2>()),
            handler
        )
    }

    /**
     * Opens the socket and runs the call. Kept separate from the public overloads so both the
     * single object and the list variant share a single implementation.
     */
    @PublishedApi
    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("TooGenericExceptionCaught")
    internal suspend fun <PAR1 : Any, PAR2 : Any> webSocketCall(
        urlPrefix: String,
        url: String,
        json: Json,
        serializerPAR1: KSerializer<PAR1>,
        serializerPAR2: KSerializer<PAR2>,
        handler: suspend (SendChannel<PAR1>, ReceiveChannel<PAR2>) -> Unit
    ) {
        val socket = Socket()
        val requestChannel = Channel<PAR1>()
        val responseChannel = Channel<PAR2>()
        try {
            socket.connect(getWebSocketUrl(urlPrefix + url.drop(1)))
            webSocketLoop(socket, url, requestChannel, responseChannel, json, serializerPAR1, serializerPAR2, handler)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            console.log(e.message)
        } finally {
            if (!requestChannel.isClosedForReceive) requestChannel.close()
            if (!responseChannel.isClosedForSend) responseChannel.close()
            // closing in `finally` matters once cancellation is rethrown, otherwise a cancelled
            // call would leak the browser socket
            socket.close()
        }
    }

    /**
     * @suppress internal function
     */
    public suspend fun Socket.receiveOrNull(): String? {
        return try {
            this.receive()
        } catch (e: SocketClosedException) {
            console.log("Socket was closed: ${e.reason}")
            null
        }
    }

    /**
     * @suppress internal function
     */
    public fun Socket.sendOrFalse(str: String): Boolean {
        return try {
            this.send(str)
            true
        } catch (e: SocketClosedException) {
            console.log("Socket was closed: ${e.reason}")
            false
        }
    }

    /**
     * Runs an RPC handler, logging any failure through the console instead of letting it escape
     * into the caller's coroutine. Cancellation is rethrown so structured concurrency still works.
     */
    @Suppress("TooGenericExceptionCaught")
    public suspend fun exceptionHelper(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            console.log(e.message)
        }
    }

    /**
     * Executes defined server-sent events connection
     */
    public suspend inline fun <reified PAR : Any> sseConnection(
        noinline function: suspend T.(SendChannel<PAR>) -> Unit,
        noinline handler: suspend (ReceiveChannel<PAR>) -> Unit
    ) {
        if (!isDom) return
        val urlPrefix = getRpcUrlPrefix()
        val (url, _) = serviceManager.requireCall(function)
        sseConnectionCall(urlPrefix, url, json, json.serializersModule.serializer<PAR>(), handler)
    }

    /**
     * Executes defined server-sent events connection with list of objects
     */
    public suspend inline fun <reified PAR : Any> sseConnection(
        noinline function: suspend T.(SendChannel<List<PAR>>) -> Unit,
        noinline handler: suspend (ReceiveChannel<List<PAR>>) -> Unit
    ) {
        if (!isDom) return
        val urlPrefix = baseUrl ?: getRpcUrlPrefix()
        val (url, _) = serviceManager.requireCall(function)
        sseConnectionCall(
            urlPrefix, url, json,
            ListSerializer(json.serializersModule.serializer<PAR>()),
            handler
        )
    }

    @PublishedApi
    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("TooGenericExceptionCaught")
    internal suspend fun <PAR : Any> sseConnectionCall(
        urlPrefix: String,
        url: String,
        json: Json,
        serializerPAR: KSerializer<PAR>,
        handler: suspend (ReceiveChannel<PAR>) -> Unit
    ) {
        val eventSource = EventSource(urlPrefix + url.drop(1), unsafeJso {
            jsSet("withCredentials", true.toJsBoolean())
        })
        val channel = Channel<PAR>()
        val events = Channel<PAR>(Channel.UNLIMITED)
        eventSource.onmessage = EventHandler { event: MessageEvent<*> ->
            if (event.data != null) {
                val response = json.decodeFromString<JsonRpcResponse>(event.data.toString())
                val par = json.decodeFromString(serializerPAR, response.requireResult())
                events.trySend(par)
            }
        }
        // the browser fires this when the stream ends or fails; without it the pump would keep
        // waiting for events that are never coming and the call would never return
        eventSource.onerror = EventHandler { _ -> events.close() }
        try {
            sseConnectionLoop(events, channel, handler)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            console.log(e.message)
        } finally {
            if (!channel.isClosedForSend) channel.close()
            // closing in `finally` matters once cancellation is rethrown, otherwise a cancelled
            // call would leak the EventSource
            eventSource.close()
        }
    }

    /**
     * Runs the three websocket pumps until the first one finishes.
     *
     * The jobs are registered before any of them starts, which is what allows a single teardown
     * lambda to cancel all of them without any `lateinit` reference. Lazy start mirrors this shape
     * across the JVM and web targets.
     */
    @PublishedApi
    @OptIn(DelicateCoroutinesApi::class)
    internal suspend fun <PAR1 : Any, PAR2 : Any> webSocketLoop(
        socket: Socket,
        url: String,
        requestChannel: Channel<PAR1>,
        responseChannel: Channel<PAR2>,
        json: Json,
        serializerPAR1: KSerializer<PAR1>,
        serializerPAR2: KSerializer<PAR2>,
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
                        if (!socket.sendOrFalse(str)) break
                    }
                } finally {
                    teardown()
                }
            }
            val responseJob = launch(start = CoroutineStart.LAZY) {
                try {
                    // the browser socket is a suspend API and the value is consumed on every
                    // iteration, so this cannot spin the way a collect-on-closed-flow can
                    while (true) {
                        val str = socket.receiveOrNull() ?: break
                        val data = parse<JsonRpcResponseJs>(str).result ?: ""
                        val par2 = json.decodeFromString(serializerPAR2, data)
                        responseChannel.send(par2)
                    }
                } finally {
                    teardown()
                }
            }
            val handlerJob = launch(start = CoroutineStart.LAZY) {
                try {
                    exceptionHelper {
                        handler(requestChannel, responseChannel)
                    }
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
     * Runs the server-sent events pump and the handler until the handler is done.
     *
     * Each job has a distinct ending role, which is what keeps the stream lossless: the event source
     * callback only marks the end of the stream, the pump hands the last events over and then closes
     * the handler channel, and the handler ends the connection. If the pump tore down instead, a fast
     * server would silently drop events for a slow consumer.
     */
    @PublishedApi
    @OptIn(DelicateCoroutinesApi::class)
    internal suspend fun <PAR : Any> sseConnectionLoop(
        events: Channel<PAR>,
        channel: Channel<PAR>,
        handler: suspend (ReceiveChannel<PAR>) -> Unit
    ) {
        coroutineScope {
            val jobs = mutableListOf<Job>()
            val teardown: () -> Unit = {
                jobs.forEach { it.cancel() }
                events.close()
                if (!channel.isClosedForSend) channel.close()
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
                    exceptionHelper {
                        handler(channel)
                    }
                } finally {
                    teardown()
                }
            }
            jobs += listOf(pumpJob, handlerJob)
            pumpJob.start()
            handlerJob.start()
        }
    }

}


