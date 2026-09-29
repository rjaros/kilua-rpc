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

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.net.InetSocketAddress
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import io.ktor.http.HttpMethod as KtorHttpMethod

// A single request as it was seen by the test server:
private class RecordedRequest(
    val method: String,
    val query: String?,
    val body: String
)

// What the test server answers with. The defaults describe a well behaved rpc server.
private class TestResponse(
    val status: Int = 200,
    val contentType: String? = "application/json",
    val body: String
)

// A GET carries no body, so the id is simply absent there. The id check is skipped for GET anyway.
private fun RecordedRequest.sentId(): String =
    Regex("\"id\"\\s*:\\s*(-?\\d+)").find(body)?.groupValues?.get(1) ?: "1"

/**
 * A declared service exception, standing in for what the kilua compiler plugin would generate
 * for a `@RpcServiceException` annotated type.
 */
@Serializable
class TestServiceException(val code: Int) : AbstractServiceException()

// Spins up a local server on a random port, that records every request it gets and answers with
// whatever the responder returns. The JDK http server is used on purpose, so that no additional
// test dependency is required.
private suspend fun withTestServer(
    responder: (RecordedRequest) -> TestResponse = { request ->
        TestResponse(body = """{"id":${request.sentId()},"result":"ok","jsonrpc":"2.0"}""")
    },
    block: suspend (String, MutableList<RecordedRequest>) -> Unit
) {
    // the server answers on its own thread, so the recorded requests are shared state
    val requests = Collections.synchronizedList(mutableListOf<RecordedRequest>())
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/") { exchange: HttpExchange ->
        try {
            val request = RecordedRequest(
                exchange.requestMethod,
                exchange.requestURI.rawQuery,
                exchange.requestBody.readBytes().decodeToString()
            )
            requests.add(request)
            val response = responder(request)
            val bytes = response.body.toByteArray()
            response.contentType?.let { exchange.responseHeaders.add("Content-Type", it) }
            exchange.sendResponseHeaders(response.status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        } finally {
            exchange.close()
        }
    }
    server.executor = null
    server.start()
    try {
        // Note: no trailing slash here on purpose, so that prefix normalization is covered too:
        block("http://127.0.0.1:${server.address.port}", requests)
    } finally {
        server.stop(0)
    }
}

val CallAgentSpec by testSuite {

    test("sends the http method matching the rpc method") {
        withTestServer { base, requests ->
            val agent = CallAgent(base)
            val methods = listOf(
                HttpMethod.GET,
                HttpMethod.POST,
                HttpMethod.PUT,
                HttpMethod.DELETE,
                HttpMethod.OPTIONS
            )
            for (method in methods) {
                val result = agent.jsonRpcCall("/rpc/routeTest", method = method)
                assertEquals("ok", result)
            }
            assertEquals(listOf("GET", "POST", "PUT", "DELETE", "OPTIONS"), requests.map { it.method })
        }
    }

    test("sends parameters as query for GET and as a json body otherwise") {
        withTestServer { base, requests ->
            val agent = CallAgent(base)
            agent.jsonRpcCall("/rpc/routeTest", listOf("\"one\"", "2"), method = HttpMethod.GET)
            agent.jsonRpcCall("/rpc/routeTest", listOf("\"one\"", "2"), method = HttpMethod.PUT)

            val get = requests[0]
            val put = requests[1]
            val getQuery = get.query ?: ""
            assertTrue(getQuery.contains("p0=%22one%22"), "GET query was $getQuery")
            assertTrue(getQuery.contains("p1=2"), "GET query was $getQuery")
            assertEquals("", get.body)
            assertNull(put.query)
            assertTrue(put.body.contains("\"jsonrpc\":\"2.0\""), "PUT body was ${put.body}")
        }
    }

    test("applies the request filter after method, body and parameters are set") {
        withTestServer { base, requests ->
            val agent = CallAgent(base)
            agent.jsonRpcCall(
                "/rpc/routeTest",
                listOf("\"one\""),
                method = HttpMethod.POST
            ) { method = KtorHttpMethod.Patch }
            // the filter runs last, so it has the final say over the method:
            assertEquals("PATCH", requests[0].method)
        }
    }

    test("converts the base url to a websocket url") {
        assertEquals("ws://host:8080/rpcws/route", getWebSocketUrl("http://host:8080/rpcws/route"))
        assertEquals("wss://host/rpcws/route", getWebSocketUrl("https://host/rpcws/route"))
        assertEquals("ws://host/rpcws/route", getWebSocketUrl("ws://host/rpcws/route"))
        assertEquals("wss://host/rpcws/route", getWebSocketUrl("wss://host/rpcws/route"))
    }

    test("hands out a distinct request id to every call") {
        withTestServer { base, requests ->
            val agent = CallAgent(base)
            agent.jsonRpcCall("/rpc/routeTest")
            agent.jsonRpcCall("/rpc/routeTest")
            agent.jsonRpcCall("/rpc/routeTest")
            val ids = requests.map { it.sentId() }
            assertEquals(listOf("1", "2", "3"), ids)
        }
    }

    test("hands out distinct request ids to concurrent calls") {
        withTestServer { base, requests ->
            val agent = CallAgent(base)
            // Dispatchers.Default escapes the test scope, so the calls really do race each other
            coroutineScope {
                (1..16).map {
                    async(Dispatchers.Default) { agent.jsonRpcCall("/rpc/routeTest") }
                }.awaitAll()
            }
            val ids = requests.map { it.sentId() }
            assertEquals(16, ids.size)
            assertEquals(16, ids.toSet().size, "ids were $ids")
        }
    }

    test("rejects a response carrying the id of a different request") {
        withTestServer(responder = {
            TestResponse(body = """{"id":999,"result":"ok","jsonrpc":"2.0"}""")
        }) { base, _ ->
            val agent = CallAgent(base)
            val thrown = assertFailsWith<Exception> { agent.jsonRpcCall("/rpc/routeTest") }
            assertEquals("Invalid response ID", thrown.message)
        }
    }

    test("does not check the response id for GET, where none is sent") {
        withTestServer(responder = {
            TestResponse(body = """{"id":999,"result":"ok","jsonrpc":"2.0"}""")
        }) { base, _ ->
            val agent = CallAgent(base)
            assertEquals("ok", agent.jsonRpcCall("/rpc/routeTest", method = HttpMethod.GET))
        }
    }

    test("reports a plain error response as an exception carrying the server message") {
        withTestServer(responder = { request ->
            TestResponse(body = """{"id":${request.sentId()},"error":"Invalid parameters","jsonrpc":"2.0"}""")
        }) { base, _ ->
            val agent = CallAgent(base)
            val thrown = assertFailsWith<Exception> { agent.jsonRpcCall("/rpc/routeTest") }
            assertEquals("Invalid parameters", thrown.message)
        }
    }

    test("rebuilds a ServiceException from the exception type reported by the server") {
        withTestServer(responder = { request ->
            TestResponse(
                body = """{"id":${request.sentId()},"error":"not allowed",""" +
                        """"exceptionType":"dev.kilua.rpc.ServiceException","jsonrpc":"2.0"}"""
            )
        }) { base, _ ->
            val agent = CallAgent(base)
            val thrown = assertFailsWith<ServiceException> { agent.jsonRpcCall("/rpc/routeTest") }
            assertEquals("not allowed", thrown.message)
        }
    }

    test("rebuilds a declared service exception from its serialized json") {
        // this is what the generated service manager does, and what makes a declared exception
        // catchable by its own type on the client
        RpcSerialization.exceptionsSerializersModule = SerializersModule {
            polymorphic(AbstractServiceException::class) {
                subclass(TestServiceException::class)
            }
        }
        val exceptionJson = RpcSerialization.getJson()
            .encodeToString<AbstractServiceException>(TestServiceException(42))
        withTestServer(responder = { request ->
            TestResponse(
                body = """{"id":${request.sentId()},"error":"failed",""" +
                        """"exceptionType":"dev.kilua.rpc.TestServiceException",""" +
                        """"exceptionJson":${Json.encodeToString(exceptionJson)},"jsonrpc":"2.0"}"""
            )
        }) { base, _ ->
            val agent = CallAgent(base)
            val thrown = assertFailsWith<TestServiceException> { agent.jsonRpcCall("/rpc/routeTest") }
            assertEquals(42, thrown.code)
        }
    }

    test("rejects a response that is neither a result nor an error") {
        withTestServer(responder = { request ->
            TestResponse(body = """{"id":${request.sentId()},"jsonrpc":"2.0"}""")
        }) { base, _ ->
            val agent = CallAgent(base)
            val thrown = assertFailsWith<Exception> { agent.jsonRpcCall("/rpc/routeTest") }
            assertEquals("Invalid response", thrown.message)
        }
    }

    test("maps an unauthorized response to a SecurityException") {
        withTestServer(responder = {
            TestResponse(status = 401, contentType = "text/plain", body = "Unauthorized")
        }) { base, _ ->
            val agent = CallAgent(base)
            assertFailsWith<SecurityException> { agent.jsonRpcCall("/rpc/routeTest") }
        }
    }

    test("maps any other failing status to an exception carrying the status description") {
        withTestServer(responder = {
            TestResponse(status = 500, contentType = "text/plain", body = "boom")
        }) { base, _ ->
            val agent = CallAgent(base)
            val thrown = assertFailsWith<Exception> { agent.jsonRpcCall("/rpc/routeTest") }
            assertEquals(HttpStatusCode.InternalServerError.description, thrown.message)
        }
    }

    test("rejects a successful response that is not json") {
        withTestServer(responder = {
            TestResponse(contentType = "text/html", body = "<html>gateway</html>")
        }) { base, _ ->
            val agent = CallAgent(base)
            val thrown = assertFailsWith<ContentTypeException> { agent.jsonRpcCall("/rpc/routeTest") }
            assertTrue(thrown.message!!.contains("text/html"), "message was ${thrown.message}")
        }
    }
}
