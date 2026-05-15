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

import io.jooby.kt.Kooby
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.logger.slf4jLogger

/**
 * Initialization function for Jooby server.
 * @param initStaticResources initialize default static resources for SPA
 * @param appDeclaration Koin modules declarations
 */
public fun Kooby.initRpcKoin(initStaticResources: Boolean = true, appDeclaration: KoinApplication.() -> Unit) {
    val koinApplication = startKoin {
        slf4jLogger()
        modules(KoinModule.joobyModule(this@initRpcKoin))
        appDeclaration()
    }
    initRpc(initStaticResources) {
        registerServiceFactory { kClass, context, _ ->
            KoinModule.threadLocalContext.set(context)
            val service = koinApplication.koin.get<Any>(kClass)
            KoinModule.threadLocalContext.remove()
            service
        }
    }
}
