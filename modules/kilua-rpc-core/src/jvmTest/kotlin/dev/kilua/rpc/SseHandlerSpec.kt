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

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

val SseHandlerSpec by testSuite {
    val deSerializer = kotlinxObjectDeSerializer()
    val undeliveredFromServer = mutableListOf<String>()

    fun handleSseConnection(service: DummyServiceSse, rawServerToClient: Channel<String>, scope: CoroutineScope) {
        scope.launch {
            handleSseConnection(
                deSerializer,
                rawServerToClient,
                String.serializer(),
                service
            ) { sendChannel -> service.serviceMethod(sendChannel) }
        }
    }

    fun <T> runTest(service: DummyServiceSse, rawServerToClient: Channel<String>, test: suspend () -> T): T =
        runBlocking {
            handleSseConnection(service, rawServerToClient, this)
            withContext(Dispatchers.Default) {
                service.waitUntilInitialized()
                val result = test()
                service.endSession()
                result
            }
        }

    @OptIn(DelicateCoroutinesApi::class)
    fun assertChannelsClosedNoUndelivered(rawServerToClient: Channel<String>, undeliveredFromServer: List<String>) {
        assertTrue(
            rawServerToClient.isClosedForReceive,
            "rawOutgoing.isClosedForReceive",
        )
        assertTrue(
            rawServerToClient.isClosedForSend,
            "rawOutgoing.isClosedForSend",
        )
        assertTrue(
            undeliveredFromServer.isEmpty()
        )
    }

    testFixture {
        val service = DummyServiceSse()
        val rawServerToClient = Channel<String> { undeliveredFromServer += it }
        service to rawServerToClient
    } asParameterForEach {
        test("writesDataGeneratedByFunctionToOutChannel") { (service, rawServerToClient) ->
            // setup
            val message = "a message from server to client"

            // execution
            val actual = runTest(service, rawServerToClient) {
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
        test("closesChannels_ifFunctionThrows") { (service, rawServerToClient) ->
            // execution (and throws evaluation)
            assertFailsWith<DummyExceptionSse> {
                runTest(service, rawServerToClient) {
                    service.beforeReturn = { throw DummyExceptionSse() }
                }
            }
            // evaluation
            assertChannelsClosedNoUndelivered(rawServerToClient, undeliveredFromServer)
        }
    }
}

private class DummyExceptionSse : Exception("dummy exception")

private class DummyServiceSse {
    var beforeReturn = {}
    lateinit var outgoing: SendChannel<String>

    private val initialized = Mutex(true)
    private val mayComplete = Mutex(true)

    suspend fun serviceMethod(outgoing: SendChannel<String>) {
        require(!this::outgoing.isInitialized)
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
