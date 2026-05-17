/*
 * Copyright (c) 2024 Robert Jaros
 * Copyright (c) 2020 Yannik Hampe
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

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

val WebSocketHandlerSpec by testSuite {
    val deSerializer = kotlinxObjectDeSerializer()
    val undeliveredFromClient = mutableListOf<String>()
    val undeliveredFromServer = mutableListOf<String>()

    @OptIn(DelicateCoroutinesApi::class)
    fun assertChannelsClosedNoUndelivered(rawClientToServer: Channel<String>, rawServerToClient: Channel<String>) {
        assertTrue(rawClientToServer.isClosedForReceive, "rawIncoming.isClosedForReceive")
        assertTrue(rawClientToServer.isClosedForSend, "rawIncoming.isClosedForSend")
        assertTrue(rawServerToClient.isClosedForReceive, "rawOutgoing.isClosedForReceive")
        assertTrue(rawServerToClient.isClosedForSend, "rawOutgoing.isClosedForSend")
        assertTrue(undeliveredFromClient.isEmpty(), "undeliveredFromClient.isEmpty")
        assertTrue(undeliveredFromServer.isEmpty(), "undeliveredFromServer.isEmpty")
    }

    fun handleWebsocketConnection(
        service: DummyService,
        rawClientToServer: Channel<String>,
        rawServerToClient: Channel<String>,
        scope: CoroutineScope
    ) {
        scope.launch {
            handleWebsocketConnection(
                deSerializer,
                rawClientToServer,
                rawServerToClient,
                String.serializer(),
                String.serializer(),
                service
            ) { receiveChannel, sendChannel -> service.serviceMethod(receiveChannel, sendChannel) }
        }
    }

    fun <T> runTest(
        service: DummyService,
        rawClientToServer: Channel<String>,
        rawServerToClient: Channel<String>,
        test: suspend () -> T
    ): T =
        runBlocking {
            handleWebsocketConnection(service, rawClientToServer, rawServerToClient, this)
            withContext(Dispatchers.Default) {
                service.waitUntilInitialized()
                val result = test()
                service.endSession()
                result
            }
        }

    suspend fun sendMessage(rawClientToServer: Channel<String>, message: String) {
        rawClientToServer.send(
            deSerializer.serializeNonNull(
                JsonRpcRequest(
                    id = 42,
                    method = "dummy",
                    params = listOf(deSerializer.serializeNonNull(message))
                )
            )
        )
    }

    testFixture {
        val service = DummyService()
        val rawClientToServer: Channel<String> = Channel { undeliveredFromClient += it }
        val rawServerToClient: Channel<String> = Channel { undeliveredFromServer += it }
        Triple(service, rawClientToServer, rawServerToClient)
    } asParameterForEach {
        test("passesParametersToFunction") { (service, rawClientToServer, rawServerToClient) ->
            // setup
            val message = "a message from client to server"

            // execution
            val actual = runTest(service, rawClientToServer, rawServerToClient) {
                sendMessage(rawClientToServer, message)
                service.incoming.receive()
            }

            // evaluation
            assertChannelsClosedNoUndelivered(rawClientToServer, rawServerToClient)
            assertEquals(actual, message)
        }
        test("writesDataGeneratedByFunctionToOutChannel") { (service, rawClientToServer, rawServerToClient) ->
            // setup
            val message = "a message from server to client"

            // execution
            val actual = runTest(service, rawClientToServer, rawServerToClient) {
                service.outgoing.send(message)
                rawServerToClient.receive()
            }

            // evaluation
            assertEquals(
                // note that `result` is encoded as JSON within the JSON, thus double quotes:
                "\"$message\"",
                deSerializer.deserialize<JsonRpcResponse>(actual).result,
            )
        }
        test("closesChannels_ifFunctionThrows") { (service, rawClientToServer, rawServerToClient) ->
            // execution (and throws evaluation)
            assertFailsWith<DummyException> {
                runTest(service, rawClientToServer, rawServerToClient) {
                    service.beforeReturn = { throw DummyException() }
                }
            }

            // evaluation
            assertChannelsClosedNoUndelivered(rawClientToServer, rawServerToClient)
        }
        test("closesOnInvalidDataFromClient") { (service, rawClientToServer, rawServerToClient) ->
            // execution (and expect evaluation)
            assertFailsWith<SerializationException> {
                runTest(service, rawClientToServer, rawServerToClient) {
                    rawClientToServer.send("invalid")
                }
            }

            // evaluation
            assertChannelsClosedNoUndelivered(rawClientToServer, rawServerToClient)
        }
    }
}

private class DummyException : Exception("dummy exception")

private class DummyService {
    var beforeReturn = {}
    lateinit var incoming: ReceiveChannel<String>
    lateinit var outgoing: SendChannel<String>

    private val initialized = Mutex(true)
    private val mayComplete = Mutex(true)

    suspend fun serviceMethod(incoming: ReceiveChannel<String>, outgoing: SendChannel<String>) {
        require(!this::incoming.isInitialized && !this::outgoing.isInitialized)
        this.incoming = incoming
        this.outgoing = outgoing
        initialized.unlock()
        // the actual logic happens in the test methods, so we just wait here until the test method signals that we
        // may complete
        mayComplete.lock()
        beforeReturn()
    }

    suspend fun waitUntilInitialized() {
        initialized.lock()
        initialized.unlock()
    }

    fun endSession() {
        mayComplete.unlock()
    }
}
