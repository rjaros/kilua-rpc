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

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Options
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject

/**
 * Controller for handling automatic routes.
 */
@Controller("/rpc")
public open class RpcController {

    @Inject
    public lateinit var rpcManagers: RpcManagers

    @Inject
    public lateinit var applicationContext: ApplicationContext

    @PostConstruct
    public fun init() {
        rpcManagers.services.forEach {
            it.deSerializer = kotlinxObjectDeSerializer(rpcManagers.serializersModules)
        }
    }

    @Get("/{+path}")
    public suspend fun get(@PathVariable path: String?, request: HttpRequest<*>): HttpResponse<String> =
        handle(HttpMethod.GET, "/rpc/$path", request)

    @Suppress("UNUSED_PARAMETER")
    @Post("/{+path}")
    public suspend fun post(
        @PathVariable path: String?,
        request: HttpRequest<*>,
        @Body body: JsonRpcRequest
    ): HttpResponse<String> = handle(HttpMethod.POST, "/rpc/$path", request)

    @Suppress("UNUSED_PARAMETER")
    @Put("/{+path}")
    public suspend fun put(
        @PathVariable path: String?,
        request: HttpRequest<*>,
        @Body body: JsonRpcRequest
    ): HttpResponse<String> = handle(HttpMethod.PUT, "/rpc/$path", request)

    @Suppress("UNUSED_PARAMETER")
    @Delete("/{+path}")
    public suspend fun delete(
        @PathVariable path: String?,
        request: HttpRequest<*>,
        @Body body: JsonRpcRequest
    ): HttpResponse<String> = handle(HttpMethod.DELETE, "/rpc/$path", request)

    @Suppress("UNUSED_PARAMETER")
    @Options("/{+path}")
    public suspend fun options(
        @PathVariable path: String?,
        request: HttpRequest<*>,
        @Body body: JsonRpcRequest
    ): HttpResponse<String> = handle(HttpMethod.OPTIONS, "/rpc/$path", request)

    private suspend fun handle(method: HttpMethod, path: String, request: HttpRequest<*>): HttpResponse<String> {
        val handler = rpcManagers.services.asSequence().mapNotNull {
            it.routeMapRegistry.findHandler(method, path)
        }.firstOrNull() ?: return HttpResponse.notFound()
        return handler(
            request,
            RequestHolder.threadLocalRequest,
            ResponseMutatorHolder.threadLocalResponseMutator,
            applicationContext
        )
    }
}
