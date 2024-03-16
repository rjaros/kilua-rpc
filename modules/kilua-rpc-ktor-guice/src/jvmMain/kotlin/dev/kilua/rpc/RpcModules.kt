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
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Module
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import kotlinx.serialization.json.Json

private const val DEFAULT_INIT_SPA = true

/**
 * Initialization function for Ktor server.
 */
public fun Application.initRpc(vararg modules: Module): Injector =
    initRpc(DEFAULT_INIT_SPA, DefaultJson, *modules)

/**
 * Initialization function for Ktor server with custom JsonSerializer.
 * @param json custom or default JsonSerializer
 */
public fun Application.initRpc(json: Json, vararg modules: Module): Injector =
    this.initRpc(DEFAULT_INIT_SPA, json, *modules)

/**
 * Initialization function for Ktor server.
 * @param initSinglePageApplication initialize default static resources for SPA
 */
public fun Application.initRpc(initSinglePageApplication: Boolean, vararg modules: Module): Injector =
    this.initRpc(initSinglePageApplication, DefaultJson, *modules)

/**
 * Initialization function for Ktor server.
 * @param initSinglePageApplication initialize default static resources for SPA
 * @param json custom or default JsonSerializer
 */
public fun Application.initRpc(initSinglePageApplication: Boolean, json: Json, vararg modules: Module): Injector {
    install(ContentNegotiation) {
        json(json)
    }

    if (initSinglePageApplication) initSinglePageApplication()

    @Suppress("SpreadOperator")
    val injector = Guice.createInjector(MainModule(this), *modules)

    intercept(ApplicationCallPipeline.Plugins) {
        call.attributes.put(injectorKey, injector.createChildInjector(CallModule(call)))
    }
    return injector

}

/**
 * Initialize default static resources for Ktor server.
 */
public fun Application.initSinglePageApplication() {
    routing {
        singlePageApplication {
            defaultPage = "index.html"
            filesPath = "/assets"
            useResources = true
        }
    }
}

internal val injectorKey: AttributeKey<Injector> = AttributeKey("injector")

public val ApplicationCall.injector: Injector get() = attributes[injectorKey]

internal class CallModule(private val call: ApplicationCall) : AbstractModule() {
    override fun configure() {
        bind(ApplicationCall::class.java).toInstance(call)
    }
}

internal class MainModule(private val application: Application) : AbstractModule() {
    override fun configure() {
        bind(Application::class.java).toInstance(application)
    }
}

/**
 * @suppress internal class
 */
public class WsSessionModule(private val webSocketSession: WebSocketServerSession) :
    AbstractModule() {
    override fun configure() {
        bind(WebSocketServerSession::class.java).toInstance(webSocketSession)
    }
}

/**
 * @suppress internal class
 */
public class DummyWsSessionModule : AbstractModule() {
    override fun configure() {
        bind(WebSocketServerSession::class.java).toInstance(DummyWebSocketServerSession())
    }
}
