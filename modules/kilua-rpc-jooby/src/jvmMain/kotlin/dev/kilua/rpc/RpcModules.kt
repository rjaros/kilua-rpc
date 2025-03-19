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

import io.jooby.MediaType
import io.jooby.jackson.JacksonModule
import io.jooby.kt.Kooby

private const val DEFAULT_INIT_RESOURCES = true

internal const val RPC_KOOBY_KEY: String = "dev.kilua.rpc.kooby.key"

/**
 * Initialization function for Jooby server.
 */
public fun Kooby.initRpc(serviceRegistration: ServiceRegistryContext.() -> Unit) =
    initRpc(DEFAULT_INIT_RESOURCES, serviceRegistration)

/**
 * Initialization function for Jooby server.
 * @param initStaticResources initialize default static resources
 */
public fun Kooby.initRpc(initStaticResources: Boolean, serviceRegistration: ServiceRegistryContext.() -> Unit) {
    if (initStaticResources) initStaticResources()
    install(JacksonModule())
    ServiceRegistry.serviceRegistration()
    before { ctx ->
        ctx.setAttribute(RPC_KOOBY_KEY, this)
    }
    use {
        val response = next.apply(ctx)
        if (ctx.requestPath.endsWith(".wasm")) {
            ctx.responseType = MediaType.valueOf("application/wasm")
        }
        response
    }
}
