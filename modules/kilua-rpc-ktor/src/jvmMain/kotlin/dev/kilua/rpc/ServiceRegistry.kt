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

import io.ktor.server.application.*
import io.ktor.server.websocket.*
import kotlin.reflect.KClass

/**
 * Provides methods to manually register RPC services.
 */
public interface ServiceRegistryContext {
    /**
     * Register RPC service class using Ktor's ApplicationCall (or not using anything).
     */
    public fun <Service : Any> registerService(
        serviceKClass: KClass<Service>,
        serviceFactory: (ApplicationCall) -> Service,
    )

    /**
     * Register RPC service class using Ktor's ApplicationCall and/or WebSocketServerSession.
     */
    public fun <Service : Any> registerService(
        serviceKClass: KClass<Service>,
        serviceFactory: (ApplicationCall, WebSocketServerSession) -> Service,
    )
}

/**
 * Register RPC service class using Ktor's ApplicationCall (or not using anything).
 */
public inline fun <reified Service : Any> ServiceRegistryContext.registerService(
    noinline serviceFactory: (ApplicationCall) -> Service,
) {
    registerService(Service::class, serviceFactory)
}

/**
 * Register RPC service class using Ktor's ApplicationCall and/or WebSocketServerSession.
 */
public inline fun <reified Service : Any> ServiceRegistryContext.registerService(
    noinline serviceFactory: (ApplicationCall, WebSocketServerSession) -> Service,
) {
    registerService(Service::class, serviceFactory)
}

/**
 * Keeps registered services in a map.
 */
internal object ServiceRegistry : ServiceRegistryContext {
    val services = mutableMapOf<KClass<*>, (ApplicationCall, WebSocketServerSession) -> Any>()

    override fun <Service : Any> registerService(
        serviceKClass: KClass<Service>,
        serviceFactory: (ApplicationCall) -> Service,
    ) {
        services[serviceKClass] = { call, _ -> serviceFactory(call) }
    }

    override fun <Service : Any> registerService(
        serviceKClass: KClass<Service>,
        serviceFactory: (ApplicationCall, WebSocketServerSession) -> Service,
    ) {
        services[serviceKClass] = serviceFactory
    }
}
