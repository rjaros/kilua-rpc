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

import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.ext.web.Router
import kotlinx.serialization.modules.SerializersModule

/**
 * Initialization function for Vert.x server with Metro.
 * @param rpcGraphFactory factory to create RpcGraph instance
 * @param router Vert.x router
 * @param initStaticResources initialize default static resources for SPA
 */
public fun Vertx.initRpcMetro(rpcGraphFactory: RpcGraph.Factory, router: Router, initStaticResources: Boolean = true) {
    initRpc(router, initStaticResources) {
        registerServiceFactory { kClass, routingContext, _, serverWebSocket ->
            val rpcGraph = rpcGraphFactory.create(this@initRpcMetro, routingContext, serverWebSocket)
            rpcGraph.services[kClass]
                ?: throw IllegalStateException("Service not found for class: ${kClass.simpleName}")
        }
    }
}

/**
 * Initialization function for Vert.x server with Metro and support for WebSockets.
 * @param rpcGraphFactory factory to create RpcGraph instance
 * @param router Vert.x router
 * @param server Vert.x HTTP server
 * @param wsServiceManagers list of WebSocket service managers
 * @param serializersModules list of serializers modules for WebSocket services
 * @param initStaticResources initialize default static resources for SPA
 */
public fun Vertx.initRpcMetro(
    rpcGraphFactory: RpcGraph.Factory,
    router: Router,
    server: HttpServer,
    wsServiceManagers: List<RpcServiceManager<*>>,
    serializersModules: List<SerializersModule>? = null,
    initStaticResources: Boolean = true,
) {
    initRpc(router, server, wsServiceManagers, serializersModules, initStaticResources) {
        registerServiceFactory { kClass, routingContext, _, serverWebSocket ->
            val rpcGraph = rpcGraphFactory.create(this@initRpcMetro, routingContext, serverWebSocket)
            rpcGraph.services[kClass]
                ?: throw IllegalStateException("Service not found for class: ${kClass.simpleName}")
        }
    }
}
