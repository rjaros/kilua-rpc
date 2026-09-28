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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
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

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.server.websocket.*
import io.ktor.sse.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// The test service mirrors the shape of a generated interface: the client resolves a route from
// the *function name*, which is why both overloads of a name share a single endpoint.
private interface IStreamService {
    suspend fun streamService(input: ReceiveChannel<Int>, output: SendChannel<String>)
    suspend fun streamService(handler: suspend (SendChannel<Int>, ReceiveChannel<String>) -> Unit)

    suspend fun eventsService(output: SendChannel<String>)
    suspend fun eventsService(handler: suspend (ReceiveChannel<String>) -> Unit)

    suspend fun listStreamService(input: ReceiveChannel<Int>, output: SendChannel<List<String>>)
    suspend fun listStreamService(handler: suspend (SendChannel<Int>, ReceiveChannel<List<String>>) -> Unit)

    suspend fun listEventsService(output: SendChannel<List<String>>)
    suspend fun listEventsService(handler: suspend (ReceiveChannel<List<String>>) -> Unit)
}

private const val WS_ROUTE = "/rpcws/routeStream"
private const val SSE_ROUTE = "/rpcsse/routeEvents"
private const val WS_LIST_ROUTE = "/rpcws/routeListStream"
private const val SSE_LIST_ROUTE = "/rpcsse/routeListEvents"

private const val SSE_TOTAL_EVENTS = 40

private class StreamManager : RpcServiceMgr<IStreamService> {
    private val routes = mapOf(
        "streamService" to WS_ROUTE,
        "eventsService" to SSE_ROUTE,
        "listStreamService" to WS_LIST_ROUTE,
        "listEventsService" to SSE_LIST_ROUTE
    )

    override fun getCall(function: Function<*>): Pair<String, HttpMethod>? =
        routes[getCallName(function)]?.let { it to HttpMethod.GET }
}

private fun agentFor(base: String): RpcAgent<IStreamService> = RpcAgent(base, StreamManager())

// The generated code exposes the agent as a service; the test only needs the two streaming
// operations, so they are declared explicitly instead of running the KSP processor.
private fun RpcAgent<IStreamService>.streamService(): IStreamService = object : IStreamService {
    override suspend fun streamService(input: ReceiveChannel<Int>, output: SendChannel<String>) =
        error("server side only")

    override suspend fun streamService(
        handler: suspend (SendChannel<Int>, ReceiveChannel<String>) -> Unit
    ) = webSocket(IStreamService::streamService, handler)

    override suspend fun eventsService(output: SendChannel<String>) = error("server side only")

    override suspend fun eventsService(handler: suspend (ReceiveChannel<String>) -> Unit) =
        sseConnection(IStreamService::eventsService, handler)

    override suspend fun listStreamService(
        input: ReceiveChannel<Int>,
        output: SendChannel<List<String>>
    ) = error("server side only")

    override suspend fun listStreamService(
        handler: suspend (SendChannel<Int>, ReceiveChannel<List<String>>) -> Unit
    ) = webSocket(IStreamService::listStreamService, handler)

    override suspend fun listEventsService(output: SendChannel<List<String>>) =
        error("server side only")

    override suspend fun listEventsService(handler: suspend (ReceiveChannel<List<String>>) -> Unit) =
        sseConnection(IStreamService::listEventsService, handler)
}

/**
 * testBalloon runs every test inside a kotlinx.coroutines.test TestScope, where `withTimeout` uses
 * *virtual* time and fires the moment the coroutine waits on a real socket. Escaping to a blocking
 * context restores real time, which is what a socket based test needs. Everything therefore happens
 * inside [withRealTime], and a failure means the operation under test never finished.
 */
private fun <T> withRealTime(block: suspend () -> T): T = runBlocking {
    withTimeout(60.seconds) {
        withContext(Dispatchers.Default) { block() }
    }
}

/**
 * Runs a real Ktor server speaking the JSON-RPC wire format, so the client is exercised over an
 * actual socket. The websocket echoes each integer back, the sse route pushes a fixed number of
 * events and then ends the stream.
 */
private suspend fun withStreamingServer(block: suspend (RpcAgent<IStreamService>) -> Unit) {
    val server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
        install(WebSockets)
        install(SSE)
        routing {
            webSocket(WS_ROUTE) {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    // the binder puts the *encoded* value into `result`, so a String result is
                    // itself quoted inside the json string - see serializeMessage in SseHandler
                    send(Frame.Text("""{"id":1,"result":"\"I'v got: 42\"","jsonrpc":"2.0"}"""))
                }
            }
            webSocket(WS_LIST_ROUTE) {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    send(Frame.Text("""{"id":1,"result":"[\"one\",\"two\"]","jsonrpc":"2.0"}"""))
                }
            }
            sse(SSE_ROUTE) {
                for (i in 0 until SSE_TOTAL_EVENTS) {
                    send(ServerSentEvent(data = """{"id":$i,"result":"\"event $i\"","jsonrpc":"2.0"}"""))
                }
            }
            sse(SSE_LIST_ROUTE) {
                for (i in 0 until SSE_TOTAL_EVENTS) {
                    send(ServerSentEvent(data = """{"id":$i,"result":"[\"event $i\"]","jsonrpc":"2.0"}"""))
                }
            }
        }
    }
    server.start()
    try {
        val port = server.engine.resolvedConnectors().first().port
        block(agentFor("http://127.0.0.1:$port/"))
    } finally {
        // The client keeps its (shared) connection pool alive, which can make stop() block for as
        // long as the socket lives. Never let shutdown hang the test: the port is ephemeral, so
        // giving up on a slow stop is harmless.
        val done = CountDownLatch(1)
        Thread {
            runCatching { server.stop(0, 2_000) }
            done.countDown()
        }.apply { isDaemon = true }.start()
        done.await(5, TimeUnit.SECONDS)
    }
}

val RpcAgentLifecycleSpec by testSuite {

    test("streaming calls end and deliver every event") {
        withRealTime {
            withStreamingServer { agent ->
                val service = agent.streamService()

                // 1. a websocket exchange finishes and the call returns
                val echoed = Channel<String>(Channel.UNLIMITED)
                service.streamService { output, input ->
                    output.send(42)
                    for (r in input) {
                        echoed.send(r)
                        break
                    }
                }
                assertEquals("I'v got: 42", echoed.receive())

                // 2. every sse event arrives even though the handler consumes them slowly, which
                //    is what used to silently drop events
                val received = mutableListOf<String>()
                service.eventsService { input ->
                    for (e in input) {
                        received.add(e)
                        delay(3)
                    }
                }
                assertEquals(
                    SSE_TOTAL_EVENTS,
                    received.size,
                    "expected all $SSE_TOTAL_EVENTS events, got ${received.size}"
                )
                assertTrue(received.first() == "event 0", "first event was ${received.first()}")
                assertTrue(
                    received.last() == "event ${SSE_TOTAL_EVENTS - 1}",
                    "last event was ${received.last()}"
                )
            }
        }
    }

    test("list variants of the streaming calls work the same way") {
        withRealTime {
            withStreamingServer { agent ->
                val service = agent.streamService()

                // 1. the webSocket overload that returns a list of objects
                val echoed = Channel<List<String>>(Channel.UNLIMITED)
                service.listStreamService { output, input ->
                    output.send(42)
                    for (r in input) {
                        echoed.send(r)
                        break
                    }
                }
                assertEquals(listOf("one", "two"), echoed.receive())

                // 2. the sseConnection overload where every event carries a list of objects
                val received = mutableListOf<List<String>>()
                service.listEventsService { input ->
                    for (e in input) {
                        received.add(e)
                        delay(3)
                    }
                }
                assertEquals(
                    SSE_TOTAL_EVENTS,
                    received.size,
                    "expected all $SSE_TOTAL_EVENTS list events, got ${received.size}"
                )
                assertEquals(listOf("event 0"), received.first())
                assertEquals(listOf("event ${SSE_TOTAL_EVENTS - 1}"), received.last())
            }
        }
    }
}
