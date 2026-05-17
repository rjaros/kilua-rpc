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

package dev.kilua.rpc

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

val ResultSerializerSpec by testSuite {
    testFixture {
        Json {
            serializersModule = SerializersModule {
                contextual(Result::class) { args -> ResultSerializer(args[0]) }
            }
        }
    } asParameterForAll {
        test("serialize successful result instance") { json ->
            assertEquals("""{"value":"test"}""", json.encodeToString(Result.success("test")))
            assertEquals("""{"value":2}""", json.encodeToString(Result.success(2)))
        }
        test("serialize failure result instance") { json ->
            assertEquals(
                """{"error":{"message":"Message","type":"IllegalStateException"}}""", json.encodeToString(
                    Result.failure<String>(
                        IllegalStateException("Message")
                    )
                )
            )
            assertEquals(
                """{"error":{"message":"Message"}}""",
                json.encodeToString(Result.failure<Int>(Exception("Message")))
            )
        }
        test("deserialize successful result instance") { json ->
            assertEquals(Result.success("test"), json.decodeFromString("""{"value":"test"}"""))
            assertEquals(Result.success(2), json.decodeFromString("""{"value":2}"""))
        }
        test("deserialize failure result instance") { json ->
            assertFailsWith<IllegalStateException>("Message") {
                json.decodeFromString<Result<String>>("""{"error":{"message":"Message","type":"IllegalStateException"}}""")
                    .getOrThrow()
            }
            assertFailsWith<Exception>("Message") {
                json.decodeFromString<Result<Int>>("""{"error":{"message":"Message"}}""").getOrThrow()
            }
        }
    }
}
