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

import dev.kilua.rpc.ksp.RpcCodeRenderer.renderClient
import dev.kilua.rpc.ksp.RpcCodeRenderer.renderClientFunctions
import dev.kilua.rpc.ksp.RpcCodeRenderer.renderExceptions
import dev.kilua.rpc.ksp.RpcCodeRenderer.renderManager
import dev.kilua.rpc.ksp.RpcCodeRenderer.renderManagerFunctions
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How routes are chosen from the annotations a method carries. */
val RoutingRenderingSpec by testSuite {

    // Route naming is the processor's decision, not the renderer's: `namedRoutes` turns into a route
    // string before any of this runs, so what these cover is that whatever route arrived is emitted
    // verbatim, and that the HTTP method beside it is too.

    test("a method is served by the route it was given") {
        val info = interfaceInfo(functions = listOf(function("ping", route = "PingService.ping")))
        assertTrue(
            renderManager(info).contains("bind(PingService::ping, HttpMethod.POST, \"PingService.ping\")")
        )
    }

    test("a method with no route records the default for the binder to name") {
        val info = interfaceInfo(functions = listOf(function("ping")))
        assertTrue(renderManager(info).contains("bind(PingService::ping, HttpMethod.POST, null)"))
    }

    test("an explicit route is used verbatim, slashes and all") {
        val bound = interfaceInfo(functions = listOf(function("fixed", route = "/fixed/route")))
        assertTrue(renderManager(bound).contains("bind(PingService::fixed, HttpMethod.POST, \"/fixed/route\")"))
    }

    test("an explicit binding method is emitted beside the route") {
        val info = interfaceInfo(
            functions = listOf(function("custom", httpMethod = "HttpMethod.GET", route = "PingService.custom"))
        )
        assertTrue(renderManager(info).contains("bind(PingService::custom, HttpMethod.GET, \"PingService.custom\")"))
    }

    test("a binding method with no route leaves it unset") {
        val info = interfaceInfo(functions = listOf(function("custom", httpMethod = "HttpMethod.GET")))
        assertTrue(renderManager(info).contains("bind(PingService::custom, HttpMethod.GET, null)"))
    }

    test("a binding route still records a POST with that route") {
        val info = interfaceInfo(functions = listOf(function("onlyRoute", httpMethod = "HttpMethod.POST", route = "/only")))
        assertTrue(renderManager(info).contains("bind(PingService::onlyRoute, HttpMethod.POST, \"/only\")"))
    }
}

/** Which platform flavour each source set is generated against. */
val FlavourRenderingSpec by testSuite {

    test("the JVM flavour declares a client file name and a Ktor request filter") {
        val client = renderClient(interfaceInfo(), JVM_REQUEST, "PingServiceImplClient", false)
        assertTrue(client.contains("@file:JvmName(\"PingServiceImplClient\")"))
        assertTrue(client.contains("(suspend HttpRequestBuilder.() -> Unit)? = null"))
        assertTrue(client.contains("import io.ktor.client.request.HttpRequestBuilder"))
    }

    test("the web flavour uses the browser request type") {
        val client = renderClient(interfaceInfo(), WEB_REQUEST, null, false)
        assertTrue(client.contains("(suspend Request.() -> Unit)? = null"))
        assertTrue(client.contains("import web.http.Request"))
        assertTrue(!client.contains("@file:JvmName"))
    }

    test("an exported interface is exported back to JavaScript") {
        assertTrue(
            renderClient(interfaceInfo(), WEB_REQUEST, null, isExported = true)
                .contains("@JsExport")
        )
        assertTrue(
            !renderClient(interfaceInfo(), WEB_REQUEST, null, isExported = false)
                .contains("@JsExport"),
            "an interface that is not exported must not produce an exported client"
        )
    }

    test("an exported interface is a plain client on the JVM and Android flavours") {
        // `@JsExport` is a Kotlin/JS annotation, so the generator must not carry the interface's own
        // annotation into the flavours that compile against the JVM standard library. The processor
        // passes false for those flavours; this asserts the renderer honours that.
        val client = renderClient(
            interfaceInfo(),
            JVM_REQUEST,
            "PingServiceImplClient",
            isExported = false
        )
        assertTrue(!client.contains("@JsExport"), client)
        assertTrue(!client.contains("@OptIn(ExperimentalJsExport::class)"), client)
        assertTrue(!client.contains("import kotlin.js."), client)
    }

    test("the renderer exports exactly when the caller says to, not by flavour") {
        // The renderer holds no opinion of its own: the flag it is handed is the only thing that
        // decides, which is what lets the processor keep the Kotlin/JS annotation off the JVM flavour.
        val exported = renderClient(interfaceInfo(), JVM_REQUEST, null, isExported = true)
        assertTrue(exported.contains("@OptIn(ExperimentalJsExport::class)"), exported)
        assertTrue(exported.contains("@JsExport"), exported)
        assertTrue(exported.contains("import kotlin.js.ExperimentalJsExport"), exported)
        assertTrue(exported.contains("import kotlin.js.JsExport"), exported)
    }

    test("the client factory declares the expect in common and the actual elsewhere") {
        val common = renderManagerFunctions(services())
        assertTrue(common.contains("public expect inline fun <reified T : Any> getService("))
        assertTrue(common.contains("fun getAllServiceManagers()"))
        assertTrue(common.contains("fun getServiceManagers(vararg kclass: KClass<*>)"))
        assertTrue(common.contains("inline fun <reified T : Any> getServiceManager()"))

        val platform = renderClientFunctions(services(), JVM_REQUEST, "GeneratedRpcServiceManagerFunctions")
        assertTrue(
            platform.contains(
                "actual inline fun <reified T : Any> getService(baseUrl: String?, " +
                    "serializersModules: List<SerializersModule>?): T = getService(baseUrl, serializersModules, null)"
            )
        )
        assertTrue(platform.contains("@file:JvmName(\"GeneratedRpcServiceManagerFunctions\")"))
    }

    test("with no services the factory emits nothing beyond the header") {
        assertEquals(
            """
            |//
            |// GENERATED by Kilua RPC
            |//
            |

            """.trimMargin(),
            renderManagerFunctions(emptyList())
        )
        assertEquals(
            """
            |//
            |// GENERATED by Kilua RPC
            |//
            |

            """.trimMargin(),
            renderClientFunctions(emptyList(), JVM_REQUEST, null)
        )
    }
}

/** What every generated file looks like, whoever it is for. */
val ShapeRenderingSpec by testSuite {

    test("every generated file is marked as generated exactly once") {
        val info = interfaceInfo(functions = listOf(function("ping")))
        listOf(
            renderManager(info),
            renderClient(info, JVM_REQUEST, "PingServiceImplClient", false),
            renderManagerFunctions(services()),
            renderClientFunctions(services(), JVM_REQUEST, null),
            renderExceptions(exceptionNames())
        ).forEach { rendered ->
            assertEquals(1, "GENERATED by Kilua RPC".toRegex().findAll(rendered).count(), rendered)
        }
    }

    test("the exceptions file imports nothing it does not use") {
        val rendered = renderExceptions(exceptionNames())
        assertTrue(
            !rendered.contains("import kotlinx.serialization.json.Json"),
            "the unused Json import regressed"
        )
        // And it is properly spaced, rather than with the stray double space it once had.
        assertTrue(
            rendered.contains("exceptionsSerializersModule = SerializersModule {"),
            rendered
        )
    }

    test("no exceptions means an empty registration that still compiles") {
        val rendered = renderExceptions(emptyList())
        assertTrue(rendered.contains("fun registerRpcServiceExceptions() {}"))
        assertTrue(!rendered.contains("private var registered"))
    }

    test("the exceptions module registers every declared exception once") {
        val rendered = renderExceptions(exceptionNames())
        assertEquals(2, Regex("subclass\\(").findAll(rendered).count(), rendered)
        assertTrue(rendered.contains("polymorphic(AbstractServiceException::class) {"))
    }

    test("the imports collected from the interface are sorted") {
        val info = interfaceInfo(
            functions = listOf(function("kiluaTypes", returnType = "kotlin.time.Instant")),
            types = setOf("kotlin.String", "kotlin.Int", "kotlin.time.Instant")
        )
        val imports = renderClient(info, JVM_REQUEST, null, false).lines()
            .filter { it.startsWith("import ") }
            .map { it.removePrefix("import ") }
        assertTrue(imports.contains("kotlin.time.Instant"))
        // Only the collected ones are sorted; the fixed few are emitted ahead of them in source order.
        val collected = imports.filter { it.startsWith("kotlin.") && !it.startsWith("kotlinx") }
        assertEquals(collected.sorted(), collected, "collected imports should come out sorted")
    }
}

private fun services(): List<NameDetails> = listOf(
    NameDetails(packageName = "example", className = "PingServiceImpl", interfaceName = "PingService", managerName = "PingServiceManager")
)

private fun exceptionNames(): List<ExceptionNameDetails> = listOf(
    ExceptionNameDetails("example", "MyFirstException"),
    ExceptionNameDetails("example", "MySecondException")
)
