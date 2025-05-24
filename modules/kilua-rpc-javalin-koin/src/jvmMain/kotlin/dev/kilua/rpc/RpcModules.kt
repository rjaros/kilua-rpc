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

import io.javalin.Javalin
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.logger.slf4jLogger

private const val DEFAULT_INIT_RESOURCES = true

/**
 * Initialization function for Javalin server.
 */
public fun Javalin.initRpc(vararg modules: Module): Unit = initRpc(DEFAULT_INIT_RESOURCES, *modules)

/**
 * Initialization function for Javalin server.
 * @param initStaticResources initialize default static resources
 */
public fun Javalin.initRpc(initStaticResources: Boolean, vararg modules: Module) {
    if (initStaticResources) initStaticResources()
    startKoin {
        slf4jLogger()
        modules(KoinModule.javalinModule(this@initRpc), *modules)
    }
}
