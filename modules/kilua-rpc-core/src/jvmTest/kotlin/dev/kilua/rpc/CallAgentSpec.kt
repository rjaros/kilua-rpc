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

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import de.infix.testBalloon.framework.core.testSuite
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import io.ktor.http.HttpMethod as KtorHttpMethod

// A single request as it was seen by the test server:
private class RecordedRequest(
    val method: String,
    val query: String?,
    val body: String
)

// Spins up a local server on a random port, that records every request it gets and always
// answers with a minimal, valid JSON-RPC response. The JDK http server is used on purpose,
// so that no additional test dependency is required.
private suspend fun withTestServer(block: suspend (String, MutableList<RecordedRequest>) -> Unit) {
    val requests = mutableListOf<RecordedRequest>()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/") { exchange: HttpExchange ->
        try {
            requests.add(
                RecordedRequest(
                    exchange.requestMethod,
                    exchange.requestURI.rawQuery,
                    exchange.requestBody.readBytes().decodeToString()
                )
            )
            val response = """{"id":1,"result":"ok","jsonrpc":"2.0"}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
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
}
