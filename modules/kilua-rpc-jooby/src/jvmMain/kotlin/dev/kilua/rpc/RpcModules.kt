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

import com.google.inject.AbstractModule
import com.google.inject.Injector
import com.google.inject.Module
import io.jooby.Context
import io.jooby.MediaType
import io.jooby.guice.GuiceModule
import io.jooby.handler.AssetSource
import io.jooby.jackson.JacksonModule
import io.jooby.kt.Kooby

public const val RPC_INJECTOR_KEY: String = "dev.kilua.rpc.injector.key"

/**
 * Initialization function for Jooby server.
 */
public fun Kooby.initRpc(vararg modules: Module): Unit = initRpc(true, *modules)

/**
 * Initialization function for Jooby server.
 * @param initStaticResources initialize default static resources
 */
public fun Kooby.initRpc(initStaticResources: Boolean = true, vararg modules: Module) {
    if (initStaticResources) initStaticResources()

    install(GuiceModule(MainModule(this), *modules))
    install(JacksonModule())
    before { ctx ->
        val injector = ctx.require(Injector::class.java).createChildInjector(ContextModule(ctx))
        ctx.setAttribute(RPC_INJECTOR_KEY, injector)
    }
    use {
        val response = next.apply(ctx)
        if (ctx.requestPath.endsWith(".wasm")) {
            ctx.responseType = MediaType.valueOf("application/wasm")
        }
        response
    }
}

/**
 * Initialize default static resources for Jooby server.
 */
public fun Kooby.initStaticResources() {
    assets("/", "/assets/index.html")
    assets("/*", AssetSource.create(javaClass.classLoader, "assets"))
}

internal class MainModule(private val kooby: Kooby) : AbstractModule() {
    override fun configure() {
        bind(Kooby::class.java).toInstance(kooby)
    }
}

public class ContextModule(private val ctx: Context) : AbstractModule() {
    override fun configure() {
        bind(Context::class.java).toInstance(ctx)
    }
}
