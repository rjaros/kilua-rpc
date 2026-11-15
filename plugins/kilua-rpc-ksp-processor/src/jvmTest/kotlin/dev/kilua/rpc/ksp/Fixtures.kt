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

package dev.kilua.rpc.ksp

/**
 * The request filter parameter type each platform flavour is generated against.
 *
 * Written out rather than imported, because the Ktor client is not on this module's classpath.
 */
internal const val JVM_REQUEST = "io.ktor.client.request.HttpRequestBuilder"
internal const val WEB_REQUEST = "web.http.Request"

internal const val REMOTE_DATA = "dev.kilua.rpc.RemoteData"
internal const val CHANNEL_RECEIVE = "kotlinx.coroutines.channels.ReceiveChannel"
internal const val CHANNEL_SEND = "kotlinx.coroutines.channels.SendChannel"

/** An interface with everything but [functions] and [types] left at its defaults. */
internal fun interfaceInfo(
    packageName: String = "example",
    interfaceName: String = "PingService",
    className: String = "${interfaceName}Impl",
    managerName: String = "${interfaceName}Manager",
    functions: List<FunctionInfo> = emptyList(),
    types: Set<String> = emptySet()
): InterfaceInfo = InterfaceInfo(
    packageName = packageName,
    interfaceName = interfaceName,
    className = className,
    managerName = managerName,
    functions = functions,
    types = types
)

/** A method with everything but [name] and [kind] left at its defaults. */
internal fun function(
    name: String,
    kind: RpcMethodKind = RpcMethodKind.CALL,
    parameters: List<RenderParameter> = emptyList(),
    returnType: String? = "String",
    httpMethod: String = "HttpMethod.POST",
    route: String? = null,
    flippedParameters: List<String> = emptyList()
): FunctionInfo = FunctionInfo(
    name = name,
    kind = kind,
    parameters = parameters,
    returnType = returnType,
    httpMethod = httpMethod,
    route = route,
    flippedParameters = flippedParameters
)
