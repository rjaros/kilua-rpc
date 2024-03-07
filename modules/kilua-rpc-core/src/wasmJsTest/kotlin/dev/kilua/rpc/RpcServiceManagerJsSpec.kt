/*
 * Copyright (c) 2024 Robert Jaros
 * Copyright (c) 2020 Yannik Hampe
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

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

@Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
class Dummy {
    suspend fun param0fun(): String = ""
    suspend fun param1fun(p1: Int): String = ""
    suspend fun param2fun(p1: Int, p2: Int): String = ""
    suspend fun param3fun(p1: Int, p2: Int, p3: Int): String = ""
    suspend fun param4fun(p1: Int, p2: Int, p3: Int, p4: Int): String = ""
    suspend fun param5fun(p1: Int, p2: Int, p3: Int, p4: Int, p5: Int): String = ""
    suspend fun param6fun(p1: Int, p2: Int, p3: Int, p4: Int, p5: Int, p6: Int): String = ""
    suspend fun websocketFun(r: ReceiveChannel<Int>, s: SendChannel<Int>) {}
    suspend fun remoteDataFun(
        p1: Int?,
        p2: Int?,
        p3: List<RemoteFilter>?,
        p4: List<RemoteSorter>?,
        p5: String?
    ): RemoteData<Dummy> {
        throw UnsupportedOperationException()
    }
}

@Suppress("UNUSED_ANONYMOUS_PARAMETER")
val ALL_NON_WS_BIND_CALLS: List<(serviceManager: RpcServiceManager, method: HttpMethod, route: String?) -> Unit> =
    listOf(
        { serviceManager, method, route -> serviceManager.bind(Dummy::param0fun, method, route) },
        { serviceManager, method, route -> serviceManager.bind(Dummy::param1fun, method, route) },
        { serviceManager, method, route -> serviceManager.bind(Dummy::param2fun, method, route) },
        { serviceManager, method, route -> serviceManager.bind(Dummy::param3fun, method, route) },
        { serviceManager, method, route -> serviceManager.bind(Dummy::param4fun, method, route) },
        { serviceManager, method, route -> serviceManager.bind(Dummy::param5fun, method, route) },
        { serviceManager, method, route -> serviceManager.bind(Dummy::param6fun, method, route) },
        { serviceManager, method, route -> serviceManager.bindRemoteData(Dummy::remoteDataFun, route) },
    )

val ALL_NON_WS_CALL_NAMES = listOf(
    getCallName(Dummy::param0fun),
    getCallName(Dummy::param1fun),
    getCallName(Dummy::param2fun),
    getCallName(Dummy::param3fun),
    getCallName(Dummy::param4fun),
    getCallName(Dummy::param5fun),
    getCallName(Dummy::param6fun),
    getCallName(Dummy::remoteDataFun),
)

class RpcServiceManager : RpcServiceManagerJs<Dummy>()

class RpcServiceManagerJsSpec {

    private lateinit var serviceManager: RpcServiceManager

    @BeforeTest
    fun beforeMethod() {
        serviceManager = RpcServiceManager()
    }

    @Test
    fun bind_addsFunctions() {
        // execution
        ALL_NON_WS_BIND_CALLS.forEach { it(serviceManager, HttpMethod.POST, null) }

        // evaluation
        val registry = serviceManager.calls.entries.sortedBy { it.value.first }

        assertEquals(ALL_NON_WS_BIND_CALLS.size, registry.size, "number of registered endpoints")
        for (i in ALL_NON_WS_BIND_CALLS.indices) {
            assertEndpointMatches(
                endpoint = registry[i],
                functionName = ALL_NON_WS_CALL_NAMES[i],
                httpMethod = HttpMethod.POST,
                expectedRoute = "/rpc/routeRpcServiceManager$i"
            )
        }
    }

    @Test
    fun bind_addsFunctions_withCustomRouteIfSupplied() {
        // execution
        ALL_NON_WS_BIND_CALLS.forEachIndexed { index, bind ->
            bind(serviceManager, HttpMethod.POST, "customRoute$index")
        }

        // evaluation
        val registry = serviceManager.calls.entries.sortedBy { it.value.first }
        assertEquals(ALL_NON_WS_BIND_CALLS.size, registry.size, "number of registered endpoints")
        for (i in ALL_NON_WS_BIND_CALLS.indices) {
            assertEndpointMatches(
                endpoint = registry[i],
                functionName = ALL_NON_WS_CALL_NAMES[i],
                httpMethod = HttpMethod.POST,
                expectedRoute = "/rpc/customRoute$i"
            )
        }
    }

    @Test
    fun bind_addsWebsocketFunction() {
        // execution
        serviceManager.bind(Dummy::websocketFun, null)

        // evaluation
        assertEndpointMatches(
            endpoint = serviceManager.calls.entries.single(),
            functionName = getCallName(Dummy::websocketFun),
            httpMethod = HttpMethod.POST,
            expectedRoute = "/rpcws/routeRpcServiceManager0"
        )
    }

    @Test
    fun bind_addsWebsocketFunctionWithCustomRoute_ifSupplied() {
        // execution
        serviceManager.bind(Dummy::websocketFun, "customWsRoute")

        // evaluation
        assertEndpointMatches(
            endpoint = serviceManager.calls.entries.single(),
            functionName = getCallName(Dummy::websocketFun),
            httpMethod = HttpMethod.POST,
            expectedRoute = "/rpcws/customWsRoute"
        )
    }

    @Test
    fun bind_addFunctionIfMethodIsGetAndFunctionHasNoParameters() {
        // execution
        serviceManager.bind(Dummy::param0fun, HttpMethod.GET, null)

        // evaluation
        assertEndpointMatches(
            endpoint = serviceManager.calls.entries.single(),
            functionName = getCallName(Dummy::param0fun),
            httpMethod = HttpMethod.GET,
            expectedRoute = "/rpc/routeRpcServiceManager0"
        )
    }


    private fun assertEndpointMatches(
        endpoint: Map.Entry<String, Pair<String, HttpMethod>>,
        functionName: String,
        httpMethod: HttpMethod,
        expectedRoute: String
    ) {
        val actualCallName = endpoint.key
        val (actualPath, actualHttpMethod) = endpoint.value

        assertEquals(functionName, actualCallName)
        assertEquals(httpMethod, actualHttpMethod, "http method")
        assertEquals(actualPath, expectedRoute)
    }
}
