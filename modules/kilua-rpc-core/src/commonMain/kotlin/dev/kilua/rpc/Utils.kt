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

/**
 * Returns the result of a JSON-RPC response or throws, so that a streaming caller can surface a
 * server error instead of dereferencing a null `result`. Mirrors the HTTP path in CallAgent.
 */
@PublishedApi
internal fun JsonRpcResponse.requireResult(): String {
    val error = this.error
    if (error != null) {
        throw Exception(error)
    }
    return this.result ?: throw Exception("Invalid response")
}

/**
 * Rewrites an http url to its websocket counterpart, or returns null when [url] is not an absolute
 * http(s) url. Only the scheme is mapped - resolving a relative url against the current page is up
 * to the caller, which is the only place that knows what the current page is.
 */
@PublishedApi
internal fun httpToWebSocketUrl(url: String): String? {
    return when {
        url.startsWith("https://") -> "wss://" + url.drop(8)
        url.startsWith("http://") -> "ws://" + url.drop(7)
        else -> null
    }
}
