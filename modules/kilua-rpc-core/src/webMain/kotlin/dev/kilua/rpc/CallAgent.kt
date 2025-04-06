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

import js.core.JsAny
import js.core.JsPrimitives.toJsString
import js.globals.globalThis
import js.objects.jso
import js.reflect.unsafeCast
import js.uri.encodeURIComponent
import web.http.BodyInit
import web.http.Headers
import web.http.RequestInit
import web.http.RequestMethod
import web.http.fetchAsync
import web.url.URLSearchParams
import kotlin.js.undefined

public external class JsonRpcResponseJs : JsAny {
    public var id: Int
    public val result: String?
    public val error: String?
    public val exceptionType: String?
    public val exceptionJson: String?
}

/**
 * HTTP status unauthorized (401).
 */
public const val HTTP_UNAUTHORIZED: Int = 401

/**
 * HTTP response body types.
 */
public enum class ResponseBodyType {
    JSON,
    TEXT,
    READABLE_STREAM
}

/**
 * An exception thrown when the server returns a non-json response for a json-rpc call.
 */
public class ContentTypeException(message: String) : Exception(message)

/**
 * An agent responsible for remote calls.
 */
public open class CallAgent {

    private var counter = 1

    /**
     * Makes an JSON-RPC call to the remote server.
     * @param url a URL address
     * @param data data to be sent
     * @param method an HTTP method
     * @param requestFilter a request filtering function
     * @return a promise of the result
     */
    @Suppress("ComplexMethod")
    public suspend fun jsonRpcCall(
        url: String,
        data: List<String?> = listOf(),
        method: HttpMethod = HttpMethod.POST,
        requestFilter: (suspend RequestInit.() -> Unit)? = null
    ): String {
        val rpcUrlPrefix = globalThis.jsGet("rpc_url_prefix")
        val urlPrefix: String = if (rpcUrlPrefix != undefined) "$rpcUrlPrefix/" else ""
        val jsonRpcRequest = JsonRpcRequest(counter++, url, data)
        val body =
            if (method == HttpMethod.GET) null else BodyInit(RpcSerialization.plain.encodeToString(jsonRpcRequest))
        val requestInit = getRequestInit(method, body, "application/json")
        requestFilter?.invoke(requestInit)
        val urlAddr = urlPrefix + url.drop(1)
        val fetchUrl = if (method == HttpMethod.GET) {
            val urlSearchParams = URLSearchParams()
            data.forEachIndexed { index, s ->
                if (s != null) urlSearchParams.append("p$index", encodeURIComponent(s))
            }
            "$urlAddr?$urlSearchParams"
        } else {
            urlAddr
        }
        val response = try {
            fetchAsync(fetchUrl, requestInit).awaitPromise()
        } catch (e: Throwable) {
            throw Exception("Failed to fetch $fetchUrl: ${e.message}")
        }
        return if (response.ok && response.headers.get("content-type") == "application/json") {
            val jsonRpcResponse = unsafeCast<JsonRpcResponseJs>(response.jsonAsync().awaitPromise()!!)
            when {
                method != HttpMethod.GET && jsonRpcResponse.id != jsonRpcRequest.id ->
                    throw Exception("Invalid response ID")

                jsonRpcResponse.error != null -> {
                    if (jsonRpcResponse.exceptionType == "dev.kilua.rpc.ServiceException") {
                        throw ServiceException(jsonRpcResponse.error)
                    } else if (jsonRpcResponse.exceptionJson != null) {
                        throw RpcSerialization.getJson()
                            .decodeFromString<AbstractServiceException>(jsonRpcResponse.exceptionJson)
                    } else {
                        throw Exception(jsonRpcResponse.error)
                    }
                }

                jsonRpcResponse.result != null -> jsonRpcResponse.result
                else -> throw Exception("Invalid response")
            }
        } else if (response.ok) {
            throw ContentTypeException("Invalid response content type: ${response.headers.get("content-type")}")
        } else {
            if (response.status.toInt() == HTTP_UNAUTHORIZED) {
                throw SecurityException(response.statusText)
            } else {
                throw Exception(response.statusText)
            }
        }
    }

    private fun getRequestMethod(httpMethod: HttpMethod): RequestMethod = when (httpMethod) {
        HttpMethod.GET -> unsafeCast("GET")
        HttpMethod.POST -> unsafeCast("POST")
        HttpMethod.PUT -> unsafeCast("PUT")
        HttpMethod.DELETE -> unsafeCast("DELETE")
        HttpMethod.OPTIONS -> unsafeCast("OPTIONS")
    }

    private fun getRequestInit(
        method: HttpMethod,
        body: JsAny?,
        contentType: String
    ): RequestInit {
        val requestMethod = getRequestMethod(method)
        val headers = Headers()
        headers.append("Content-Type", contentType)
        headers.append("X-Requested-With", "XMLHttpRequest")
        return jso {
            jsSet("method", requestMethod)
            if (body != null) jsSet("body", body)
            jsSet("headers", headers)
            jsSet("credentials", "include".toJsString())
        }
    }
}
