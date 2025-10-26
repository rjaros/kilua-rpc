/*
 * Copyright (c) 2025 Robert Jaros
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

import js.reflect.Reflect
import web.window.window
import kotlin.js.JsAny

/**
 * Function to set property on JS Object
 */
@PublishedApi
internal fun JsAny.jsSet(key: String, value: JsAny) {
    Reflect.set(this, key, value)
}

/**
 * Function to get property from JS Object
 */
@PublishedApi
internal fun JsAny.jsGet(key: String): JsAny? {
    return Reflect.get(this, key)
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

internal expect fun isDom(): Boolean

/**
 * Whether the DOM is available
 */
@PublishedApi
internal val isDom: Boolean by lazy { isDom() }
