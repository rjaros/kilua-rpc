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

import java.util.*

/**
 * Stores a mapping from an HTTP-method+path to a handler
 */
public interface RouteMapRegistry<T> {
    public fun addRoute(method: HttpMethod, path: String, handler: T)
    public fun findHandler(method: HttpMethod, path: String): T?
    public fun asSequence(method: HttpMethod): Sequence<RouteMapEntry<T>>
    public fun asSequence(): Sequence<RouteMapEntry<T>>
}

/**
 * Create a default implementation of @see RouteMapRegistry
 */
public fun <T> createRouteMapRegistry(): RouteMapRegistry<T> = RouteMapRegistryImpl()

public data class RouteMapEntry<T>(val method: HttpMethod, val path: String, val handler: T)

private class RouteMapRegistryImpl<T> : RouteMapRegistry<T> {
    private val map: MutableMap<HttpMethod, MutableMap<String, T>> = EnumMap(HttpMethod::class.java)

    override fun addRoute(method: HttpMethod, path: String, handler: T) {
        map.compute(method) { _, oldValue ->
            (oldValue ?: HashMap()).also { it[path] = handler }
        }
    }

    override fun findHandler(method: HttpMethod, path: String): T? = map[method]?.let { it[path] }

    override fun asSequence(method: HttpMethod): Sequence<RouteMapEntry<T>> {
        return (map[method] ?: return emptySequence())
            .asSequence()
            .map { (path, handler) -> RouteMapEntry(method, path, handler) }
    }

    override fun asSequence(): Sequence<RouteMapEntry<T>> =
        map.asSequence().flatMap { (method, mappings) ->
            mappings.asSequence().map { RouteMapEntry(method, it.key, it.value) }
        }
}
