/*
 * Copyright (c) 2026 Robert Jaros
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

import io.quarkus.runtime.StartupEvent
import io.vertx.core.http.ServerWebSocket
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.enterprise.inject.spi.BeanManager
import jakarta.inject.Inject
import kotlinx.serialization.modules.SerializersModule

/**
 * Quarkus CDI bean that configures HTTP, WebSocket and SSE routes via Vert.x Router.
 */
@ApplicationScoped
public open class RpcModules {

    @Inject
    public lateinit var beanManager: BeanManager

    @Inject
    public lateinit var rpcManagers: RpcManagers

    @PostConstruct
    public fun init() {
        BeanContextHolder.beanManager = beanManager
        rpcManagers.services.forEach {
            it.deSerializer = kotlinxObjectDeSerializer(rpcManagers.serializersModules)
        }
    }

    /**
     * Registers RPC HTTP, WebSocket and SSE routes on the Vert.x Router
     * at application startup.
     */
    public fun registerRoutes(@Observes event: StartupEvent, router: Router) {
        router.route().handler(BodyHandler.create())

        configureHttpRoutes(router)
        configureWebSocketRoutes(router)
        configureSseRoutes(router)
    }

    private fun configureHttpRoutes(router: Router) {
        val methods = listOf(
            io.vertx.core.http.HttpMethod.GET,
            io.vertx.core.http.HttpMethod.POST,
            io.vertx.core.http.HttpMethod.PUT,
            io.vertx.core.http.HttpMethod.DELETE,
            io.vertx.core.http.HttpMethod.OPTIONS
        )

        for (method in methods) {
            router.route(method, "/rpc/*").handler { ctx ->
                handleHttpRequest(method, ctx)
            }
        }
    }

    private fun handleHttpRequest(method: io.vertx.core.http.HttpMethod, ctx: RoutingContext) {
        val rpcMethod = when (method) {
            io.vertx.core.http.HttpMethod.GET -> dev.kilua.rpc.HttpMethod.GET
            io.vertx.core.http.HttpMethod.POST -> dev.kilua.rpc.HttpMethod.POST
            io.vertx.core.http.HttpMethod.PUT -> dev.kilua.rpc.HttpMethod.PUT
            io.vertx.core.http.HttpMethod.DELETE -> dev.kilua.rpc.HttpMethod.DELETE
            io.vertx.core.http.HttpMethod.OPTIONS -> dev.kilua.rpc.HttpMethod.OPTIONS
            else -> return notFound(ctx)
        }

        val handler = rpcManagers.services.asSequence().mapNotNull {
            it.routeMapRegistry.findHandler(rpcMethod, ctx.normalizedPath())
        }.firstOrNull()

        if (handler != null) {
            handler(ctx)
        } else {
            notFound(ctx)
        }
    }

    private fun configureWebSocketRoutes(router: Router) {
        router.route("/rpcws/*").handler { ctx ->
            ctx.request().toWebSocket().onSuccess { ws ->
                handleWebSocket(ctx, ws)
            }.onFailure {
                ctx.response().setStatusCode(400).end()
            }
        }
    }

    private fun handleWebSocket(ctx: RoutingContext, ws: ServerWebSocket) {
        val handler = rpcManagers.services.asSequence().mapNotNull {
            it.webSocketRequests[ctx.normalizedPath()]
        }.firstOrNull()

        if (handler != null) {
            handler(ctx, ctx.vertx(), ws)
        } else {
            ws.close()
        }
    }

    private fun configureSseRoutes(router: Router) {
        router.get("/rpcsse/*").handler { ctx ->
            handleSse(ctx)
        }
    }

    private fun handleSse(ctx: RoutingContext) {
        val handler = rpcManagers.services.asSequence().mapNotNull {
            it.sseRequests[ctx.normalizedPath()]
        }.firstOrNull()

        if (handler != null) {
            handler(ctx)
        } else {
            ctx.response().setStatusCode(404).end()
        }
    }

    private fun notFound(ctx: RoutingContext) {
        ctx.response().setStatusCode(404).end()
    }
}

/**
 * Data class wrapping a list of RPC service managers for CDI injection.
 */
@ApplicationScoped
public open class RpcManagers(
    public val services: List<RpcServiceManager<*>>,
    public val serializersModules: List<SerializersModule>? = null
)
