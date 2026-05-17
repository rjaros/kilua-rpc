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

import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertContains
import kotlin.test.assertEquals

const val GET_PATH_A = "get path A"
const val GET_PATH_B = "get path B"
const val POST_PATH = "post path"
const val GET_HANDLER_A = "get handler A"
const val GET_HANDLER_B = "get handler B"
const val POST_HANDLER = "post handler"

val RouteMapRegistrySpec by testSuite {
    testFixture {
        createRouteMapRegistry<String>().also { registry ->
            registry.addRoute(HttpMethod.GET, GET_PATH_A, GET_HANDLER_A)
            registry.addRoute(HttpMethod.GET, GET_PATH_B, GET_HANDLER_B)
            registry.addRoute(HttpMethod.POST, POST_PATH, POST_HANDLER)
        }
    } asParameterForEach {
        fun args(method: HttpMethod, path: String, expected: String?): Array<Any?> = arrayOf(method, path, expected)
        val provide_method_path_expected: Array<Array<Any?>> = arrayOf(
            // Get existing path
            args(HttpMethod.GET, GET_PATH_A, GET_HANDLER_A),
            // Get path that does not exist at all (should return null)
            args(HttpMethod.OPTIONS, "does not exist", null),
            // Get path that exists with different method (should return null)
            args(HttpMethod.GET, POST_PATH, null),
        )

        for (data in provide_method_path_expected) {
            test("findHandler_${data[0]}_findsExpectedHandler") { registry ->
                // execution
                val actual = registry.findHandler(data[0] as HttpMethod, data[1] as String)
                assertEquals(actual, data[2])
            }
        }

        test("asSequence_returnsAllEntries") { registry ->
            // execution
            val actual = registry.asSequence().toList()
            // evaluation
            assertContains(actual, RouteMapEntry(HttpMethod.GET, GET_PATH_A, GET_HANDLER_A))
            assertContains(actual, RouteMapEntry(HttpMethod.GET, GET_PATH_B, GET_HANDLER_B))
            assertContains(actual, RouteMapEntry(HttpMethod.POST, POST_PATH, POST_HANDLER))
        }

        fun args(method: HttpMethod, expected: Array<RouteMapEntry<String>>): Array<Any> = arrayOf(method, expected)
        val provide_method_expected: Array<Array<Any>> = arrayOf(
            args(
                HttpMethod.GET,
                arrayOf(
                    RouteMapEntry(HttpMethod.GET, GET_PATH_A, GET_HANDLER_A),
                    RouteMapEntry(HttpMethod.GET, GET_PATH_B, GET_HANDLER_B)
                )
            ),
            args(HttpMethod.POST, arrayOf(RouteMapEntry(HttpMethod.POST, POST_PATH, POST_HANDLER))),
            args(HttpMethod.DELETE, emptyArray())
        )
        for (data in provide_method_expected) {
            test("forEach_${data[0]}_runsForEachHandlerWithRequestedMethod") { registry ->
                // execution
                val actual = registry.asSequence(data[0] as HttpMethod).toList()

                @Suppress("UNCHECKED_CAST")
                val expected = data[1] as Array<Any>
                for (data2 in expected) {
                    assertContains(actual, data2)
                }
            }
        }
    }
}
