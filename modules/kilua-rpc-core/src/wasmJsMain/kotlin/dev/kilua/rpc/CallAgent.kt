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

import kotlinx.browser.window
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import org.w3c.dom.get
import org.w3c.dom.url.URLSearchParams
import org.w3c.fetch.INCLUDE
import org.w3c.fetch.RequestCredentials
import org.w3c.fetch.RequestInit
import kotlin.coroutines.resume

@PublishedApi
internal external class JsonRpcResponseJs : JsAny {
    var id: Int
    val result: String?
    val error: String?
    val exceptionType: String?
    val exceptionJson: String?
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

    private val rpcUrlPrefix = window["rpc_url_prefix"]
    private val urlPrefix: String = if (rpcUrlPrefix != null) "$rpcUrlPrefix/" else ""
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
        val requestInit = obj<RequestInit> {}
        requestInit.method = method.name
        requestInit.credentials = RequestCredentials.INCLUDE
        val jsonRpcRequest = JsonRpcRequest(counter++, url, data)
        val urlAddr = urlPrefix + url.drop(1)
        val fetchUrl = if (method == HttpMethod.GET) {
            val paramsObject = obj()
            data.forEachIndexed { index, s ->
                if (s != null) paramsObject["p$index"] = encodeURIComponent(s).toJsString()
            }
            urlAddr + "?" + URLSearchParams(paramsObject).toString()
        } else {
            requestInit.body = RpcSerialization.plain.encodeToString(jsonRpcRequest).toJsString()
            urlAddr
        }
        val headers = obj()
        headers["Content-Type"] = "application/json".toJsString()
        headers["X-Requested-With"] = "XMLHttpRequest".toJsString()
        requestInit.headers = headers
        requestFilter?.invoke(requestInit)
        return suspendCancellableCoroutine { cont: CancellableContinuation<String> ->
            window.fetch(fetchUrl, requestInit).then { response ->
                if (response.ok && response.headers.get("Content-Type") == "application/json") {
                    response.json().then { data ->
                        val dataAsResponse = data!!.unsafeCast<JsonRpcResponseJs>()
                        when {
                            method != HttpMethod.GET && dataAsResponse.id != jsonRpcRequest.id -> cont.cancel(
                                Exception("Invalid response ID")
                            )

                            dataAsResponse.error != null -> {
                                if (dataAsResponse.exceptionType == "dev.kilua.rpc.ServiceException") {
                                    cont.cancel(ServiceException(dataAsResponse.error))
                                } else if (dataAsResponse.exceptionJson != null) {
                                    cont.cancel(
                                        RpcSerialization.getJson()
                                            .decodeFromString<AbstractServiceException>(dataAsResponse.exceptionJson)
                                    )
                                } else {
                                    cont.cancel(Exception(dataAsResponse.error))
                                }
                            }

                            dataAsResponse.result != null -> cont.resume(dataAsResponse.result)
                            else -> cont.cancel(Exception("Invalid response"))
                        }
                        obj()
                    }
                } else if (response.ok) {
                    cont.cancel(ContentTypeException("Invalid response content type: ${response.headers.get("Content-Type")}"))
                } else {
                    if (response.status.toInt() == HTTP_UNAUTHORIZED) {
                        cont.cancel(SecurityException(response.statusText))
                    } else {
                        cont.cancel(Exception(response.statusText))
                    }
                }
                obj()
            }.catch {
                cont.cancel(Exception(it.toString()))
                obj()
            }
        }
    }

    /**
     * Makes a remote call to the remote server.
     * @param url a URL address
     * @param data data to be sent
     * @param method an HTTP method
     * @param contentType a content type of the request
     * @param responseBodyType response body type
     * @param requestFilter a request filtering function
     * @return a promise of the result
     */
    @Suppress("ComplexMethod")
    public suspend fun remoteCall(
        url: String,
        data: JsAny? = null,
        stringData: String? = null,
        method: HttpMethod = HttpMethod.GET,
        contentType: String = "application/json",
        responseBodyType: ResponseBodyType = ResponseBodyType.JSON,
        requestFilter: (suspend RequestInit.() -> Unit)? = null
    ): JsAny? {
        val requestInit = RequestInit()
        requestInit.method = method.name
        requestInit.credentials = RequestCredentials.INCLUDE
        val urlAddr = urlPrefix + url.drop(1)
        val fetchUrl = if (method == HttpMethod.GET) {
            urlAddr + "?" + URLSearchParams(data).toString()
        } else {
            requestInit.body = when (contentType) {
                "application/json" -> stringData?.toJsString() ?: JSON.stringify(data).toJsString()
                "application/x-www-form-urlencoded" -> URLSearchParams(data).toString().toJsString()
                else -> data
            }
            urlAddr
        }
        val headers = obj()
        headers["Content-Type"] = contentType.toJsString()
        headers["X-Requested-With"] = "XMLHttpRequest".toJsString()
        requestInit.headers = headers
        requestFilter?.invoke(requestInit)
        return suspendCancellableCoroutine { cont: CancellableContinuation<JsAny?> ->
            window.fetch(fetchUrl, requestInit).then { response ->
                if (response.ok) {
                    when (responseBodyType) {
                        ResponseBodyType.JSON -> {
                            if (response.headers.get("Content-Type") == "application/json") {
                                response.json().then {
                                    cont.resume(it)
                                    obj()
                                }
                            } else {
                                cont.cancel(
                                    ContentTypeException(
                                        "Invalid response content type: ${
                                            response.headers.get(
                                                "Content-Type"
                                            )
                                        }"
                                    )
                                )
                            }
                        }

                        ResponseBodyType.TEXT -> response.text().then {
                            cont.resume(it)
                            obj()
                        }

                        ResponseBodyType.READABLE_STREAM -> cont.resume(response.body)
                    }
                } else {
                    if (response.status.toInt() == HTTP_UNAUTHORIZED) {
                        cont.cancel(SecurityException(response.statusText))
                    } else {
                        cont.cancel(Exception(response.statusText))
                    }
                }
                obj()
            }.catch {
                cont.cancel(Exception(it.toString()))
                obj()
            }
        }
    }
}
