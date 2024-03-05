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

/**
 * JavaScript empty object.
 */
@PublishedApi
internal fun obj(): JsAny = js("({})")

/**
 * JavaScript encodeURIComponent function
 */
internal external fun encodeURIComponent(uri: String): String

/**
 * Helper function for creating JavaScript objects.
 */
@PublishedApi
internal inline fun <T : JsAny> obj(init: T.() -> Unit): T {
    @Suppress("UNCHECKED_CAST")
    return (obj() as T).apply(init)
}

private fun objSet(obj: JsAny, key: String, value: JsAny): Unit = js("{ obj[key] = value }")

@Suppress("RedundantNullableReturnType")
private fun objGet(obj: JsAny, key: String): JsAny? = js("obj[key]")

/**
 * Operator to set property on JS Object
 */
internal operator fun JsAny.set(key: String, value: JsAny) {
    objSet(this, key, value)
}

/**
 * Operator to get property from JS Object
 */
internal operator fun JsAny.get(key: String): JsAny? {
    return objGet(this, key)
}

/**
 * Creates a websocket URL from current window.location and given path.
 */
public fun getWebSocketUrl(url: String): String {
    return if (url.startsWith("https://")) {
        "wss" + url.drop(5)
    } else if (url.startsWith("http://")) {
        "ws" + url.drop(4)
    } else {
        val location = window.location
        val scheme = if (location.protocol == "https:") "wss" else "ws"
        val port = if (location.port == "8088") ":8080"
        else if (location.port != "0" && location.port != "") ":${location.port}" else ""
        "$scheme://${location.hostname}$port/$url"
    }
}
