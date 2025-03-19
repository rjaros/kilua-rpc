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

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

private const val DEFAULT_INIT_RESOURCES = true

/**
 * Initialization function for Ktor server.
 */
public fun Application.initRpc(serviceRegistration: ServiceRegistryContext.() -> Unit) =
    initRpc(DEFAULT_INIT_RESOURCES, DefaultJson, serviceRegistration)

/**
 * Initialization function for Ktor server with custom JsonSerializer.
 * @param json custom or default JsonSerializer
 */
public fun Application.initRpc(json: Json, serviceRegistration: ServiceRegistryContext.() -> Unit) =
    this.initRpc(DEFAULT_INIT_RESOURCES, json, serviceRegistration)

/**
 * Initialization function for Ktor server.
 * @param initStaticResources initialize default static resources for SPA
 */
public fun Application.initRpc(
    initStaticResources: Boolean,
    serviceRegistration: ServiceRegistryContext.() -> Unit
) =
    this.initRpc(initStaticResources, DefaultJson, serviceRegistration)

/**
 * Initialization function for Ktor server.
 * @param initStaticResources initialize default static resources for SPA
 * @param json custom or default JsonSerializer
 */
public fun Application.initRpc(
    initStaticResources: Boolean,
    json: Json,
    serviceRegistration: ServiceRegistryContext.() -> Unit
) {
    install(ContentNegotiation) {
        json(json)
    }
    if (initStaticResources) initStaticResources()
    ServiceRegistry.serviceRegistration()
}
