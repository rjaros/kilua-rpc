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

package dev.kilua.rpc.ksp

import dev.kilua.rpc.ksp.RpcCodeRenderer.renderClient
import dev.kilua.rpc.ksp.RpcCodeRenderer.renderManager
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The streaming conventions the generated client and manager follow.
 *
 * A streaming method is declared twice: once as the raw channel function, and once as the handler
 * that receives the channels the other way round. The generator emits an override for both from the
 * raw declaration, and must not emit a second pair from the handler's own declaration - whichever
 * way round they are written.
 */
val ClientRenderingSpec by testSuite {

    test("a plain method is bound once and overridden to call") {
        val info = interfaceInfo(functions = listOf(function("ping", parameters = listOf(RenderParameter("message", "String")))))
        assertEquals(
            "bind(PingService::ping, HttpMethod.POST, null)",
            managerBindsOf(renderManager(info))
        )
        assertTrue(
            renderClient(info, JVM_REQUEST, null, false).contains(
                "    override suspend fun ping(message: String) = call(PingService::ping, message)"
            )
        )
    }

    test("a method with no parameters is bound and overridden without any") {
        val info = interfaceInfo(functions = listOf(function("size", returnType = "Int")))
        assertEquals(
            "bind(PingService::size, HttpMethod.POST, null)",
            managerBindsOf(renderManager(info))
        )
        assertTrue(
            renderClient(info, JVM_REQUEST, null, false)
                .contains("    override suspend fun size() = call(PingService::size)")
        )
    }

    test("declaring the handler first produces exactly what declaring it last does") {
        val raw = function(
            "wservice",
            RpcMethodKind.WEBSOCKET,
            parameters = listOf(RenderParameter("input", "ReceiveChannel<Int>"), RenderParameter("output", "SendChannel<String>")),
            flippedParameters = listOf("SendChannel<Int>", "ReceiveChannel<String>")
        )
        val handler = function(
            "wservice",
            parameters = listOf(RenderParameter("handler", "(SendChannel<Int>, ReceiveChannel<String>) -> Unit"))
        )
        val rawFirst = interfaceInfo(functions = listOf(raw, handler))
        val handlerFirst = interfaceInfo(functions = listOf(handler, raw))
        assertEquals(
            renderManager(rawFirst),
            renderManager(handlerFirst),
            "the manager must not depend on declaration order"
        )
        assertEquals(
            renderClient(rawFirst, JVM_REQUEST, null, false),
            renderClient(handlerFirst, JVM_REQUEST, null, false),
            "the client must not depend on declaration order"
        )
    }

    test("a websocket pair binds once and overrides both declarations") {
        val info = interfaceInfo(
            functions = listOf(
                function(
                    "wservice",
                    RpcMethodKind.WEBSOCKET,
                    parameters = listOf(
                        RenderParameter("input", "ReceiveChannel<Int>"),
                        RenderParameter("output", "SendChannel<String>")
                    ),
                    flippedParameters = listOf("SendChannel<Int>", "ReceiveChannel<String>")
                ),
                function("wservice", parameters = listOf(RenderParameter("handler", "(SendChannel<Int>, ReceiveChannel<String>) -> Unit")))
            ),
            types = setOf(CHANNEL_RECEIVE, CHANNEL_SEND, "kotlin.Int")
        )
        val manager = renderManager(info)
        assertEquals(
            "bind(PingService::wservice, null as String?)",
            managerBindsOf(manager)
        )
        val client = renderClient(info, JVM_REQUEST, null, false)
        assertTrue(client.contains("    override suspend fun wservice(input: ReceiveChannel<Int>, output: SendChannel<String>) {}"))
        assertTrue(
            client.contains(
                "    override suspend fun wservice(handler: suspend (SendChannel<Int>, ReceiveChannel<String>) -> Unit) " +
                    "= webSocket(PingService::wservice, handler)"
            )
        )
        // Two overrides, no more: the empty stub for the raw declaration and the handler one. A third
        // would be the handler's own declaration turning into a call override.
        assertEquals(2, Regex("override suspend fun wservice\\(").findAll(client).count(), client)
        assertTrue(
            client.contains("wservice(handler: suspend (SendChannel<Int>, ReceiveChannel<String>) -> Unit) = webSocket"),
            client
        )
    }

    test("an SSE-only interface still imports ReceiveChannel") {
        // The declaration names SendChannel only; the flipped override names ReceiveChannel, which
        // would not be imported without the pairing.
        val info = interfaceInfo(
            functions = listOf(
                function(
                    "events",
                    RpcMethodKind.SSE,
                    parameters = listOf(RenderParameter("output", "SendChannel<String>")),
                    flippedParameters = listOf("ReceiveChannel<String>")
                ),
                function("events", parameters = listOf(RenderParameter("handler", "(ReceiveChannel<String>) -> Unit")))
            ),
            types = setOf(CHANNEL_SEND, "kotlin.String")
        )
        val client = renderClient(info, JVM_REQUEST, null, false)
        assertTrue(
            client.contains("import ${CHANNEL_RECEIVE}"),
            "an SSE-only override needs ReceiveChannel imported"
        )
        assertTrue(
            client.contains(
                "    override suspend fun events(handler: suspend (ReceiveChannel<String>) -> Unit) " +
                    "= sseConnection(PingService::events, handler)"
            )
        )
        assertEquals("bind(PingService::events, null as String?)", managerBindsOf(renderManager(info)))
    }

    test("a channel direction is rendered from the flipped type, not by rewriting text") {
        val info = interfaceInfo(
            functions = listOf(
                function(
                    "wservice",
                    RpcMethodKind.WEBSOCKET,
                    parameters = listOf(
                        RenderParameter("input", "ReceiveChannel<kotlin.time.Instant>"),
                        RenderParameter("output", "SendChannel<kotlin.time.Instant>")
                    ),
                    flippedParameters = listOf("SendChannel<kotlin.time.Instant>", "ReceiveChannel<kotlin.time.Instant>")
                )
            )
        )
        assertTrue(
            renderClient(info, JVM_REQUEST, null, false).contains(
                "handler: suspend (SendChannel<kotlin.time.Instant>, ReceiveChannel<kotlin.time.Instant>) -> Unit"
            )
        )
    }

    test("a RemoteData return is bound as remote data and constructed as one") {
        val info = interfaceInfo(
            functions = listOf(
                function(
                    "rowData",
                    RpcMethodKind.REMOTE_DATA,
                    parameters = listOf(
                        RenderParameter("page", "Int?"),
                        RenderParameter("filter", "List<RemoteFilter>?")
                    ),
                    returnType = REMOTE_DATA + "<Row>"
                )
            ),
            types = setOf("example.Row", "dev.kilua.rpc.RemoteFilter", REMOTE_DATA)
        )
        assertEquals(
            "bindRemoteData(PingService::rowData, null)",
            managerBindsOf(renderManager(info))
        )
        assertTrue(
            renderClient(info, JVM_REQUEST, null, false).contains(
                "    override suspend fun rowData(page: Int?, filter: List<RemoteFilter>?) = " +
                    "$REMOTE_DATA<Row>()"
            )
        )
    }

    test("a parameter-less RemoteData method still falls back to a call override") {
        // Recorded behaviour rather than intended behaviour: the empty-parameter branch short-circuits
        // the kind. Kept as it is so a change here is a visible diff.
        val info = interfaceInfo(
            functions = listOf(function("rows", RpcMethodKind.REMOTE_DATA, returnType = "$REMOTE_DATA<Row>"))
        )
        assertTrue(
            renderClient(info, JVM_REQUEST, null, false)
                .contains("    override suspend fun rows() = call(PingService::rows)")
        )
    }

    test("a user type merely named like a library one is an ordinary call") {
        // The generator decides by resolved declaration, not by name. These have none of the library
        // types' qualified names, so nothing special may happen to them.
        val info = interfaceInfo(
            functions = listOf(
                function(
                    "table",
                    parameters = listOf(RenderParameter("page", "Int?")),
                    returnType = "example.decoy.RemoteData<Row>"
                )
            ),
            types = setOf("example.decoy.RemoteData", "example.Row", "kotlin.Int")
        )
        val manager = renderManager(info)
        assertEquals(
            "bind(PingService::table, HttpMethod.POST, null)",
            managerBindsOf(manager),
            "a look-alike RemoteData must not be treated as a remote data slab"
        )
        assertTrue(!manager.contains("bindRemoteData"))
        assertTrue(
            renderClient(info, JVM_REQUEST, null, false)
                .contains("    override suspend fun table(page: Int?) = call(PingService::table, page)")
        )
    }
}

/**
 * The one binding a generated manager emits for a single-method interface.
 *
 * Every bind line both names the call and references the interface method. The manager declaration
 * itself also references the method, as `(PingService::class)`, which is why both markers are
 * needed rather than either alone.
 */
private fun managerBindsOf(manager: String): String? =
    manager.lines().filter { it.contains("bind") && it.contains("::") }.singleOrNull()?.trim()
