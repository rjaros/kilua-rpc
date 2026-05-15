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
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.logger.slf4jLogger

/**
 * Initialization function for Vert.x server with Koin.
 * @param router Vert.x router
 * @param initStaticResources initialize default static resources for SPA
 * @param appDeclaration Koin modules declarations
 */
public fun Vertx.initRpcKoin(
    router: Router,
    initStaticResources: Boolean = true,
    appDeclaration: KoinApplication.() -> Unit
) {
    val koinApplication = startKoin {
        slf4jLogger()
        modules(KoinModule.vertxModule(this@initRpcKoin))
        appDeclaration()
    }
    initRpc(router, initStaticResources) {
        registerServiceFactory { kClass, routingContext, _, serverWebSocket ->
            KoinModule.threadLocalRoutingContext.set(routingContext)
            KoinModule.threadLocalServerWebSocket.set(serverWebSocket)
            val service = koinApplication.koin.get<Any>(kClass)
            KoinModule.threadLocalRoutingContext.remove()
            KoinModule.threadLocalServerWebSocket.remove()
            service
        }
    }
}

/**
 * Initialization function for Vert.x server with Koin and support for WebSockets.
 * @param router Vert.x router
 * @param server Vert.x HTTP server
 * @param wsServiceManagers list of WebSocket service managers
 * @param serializersModules list of serializers modules for WebSocket services
 * @param initStaticResources initialize default static resources for SPA
 * @param appDeclaration Koin modules declarations
 */
public fun Vertx.initRpcKoin(
    router: Router,
    server: HttpServer,
    wsServiceManagers: List<RpcServiceManager<*>>,
    serializersModules: List<SerializersModule>? = null,
    initStaticResources: Boolean = true,
    appDeclaration: KoinApplication.() -> Unit
) {
    val koinApplication = startKoin {
        slf4jLogger()
        modules(KoinModule.vertxModule(this@initRpcKoin))
        appDeclaration()
    }
    initRpc(router, server, wsServiceManagers, serializersModules, initStaticResources) {
        registerServiceFactory { kClass, routingContext, _, serverWebSocket ->
            KoinModule.threadLocalRoutingContext.set(routingContext)
            KoinModule.threadLocalServerWebSocket.set(serverWebSocket)
            val service = koinApplication.koin.get<Any>(kClass)
            KoinModule.threadLocalRoutingContext.remove()
            KoinModule.threadLocalServerWebSocket.remove()
            service
        }
    }
}
