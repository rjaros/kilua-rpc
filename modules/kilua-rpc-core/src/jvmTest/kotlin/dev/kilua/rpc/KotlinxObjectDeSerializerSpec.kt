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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.test.assertEquals

val KotlinxObjectDeSerializerSpec by testSuite {
    val deSerializer = kotlinxObjectDeSerializer()

    val provideObjExpectedString: Array<Array<Any?>> = arrayOf(
        arrayOf("simple string", String.serializer(), "\"simple string\""),
        arrayOf("special {[]} chars", String.serializer(), "\"special {[]} chars\""),
        arrayOf(42, Int.serializer(), "42"),
        arrayOf(
            "firstValue" to "secondValue",
            PairSerializer(String.serializer(), String.serializer()),
            """{"first":"firstValue","second":"secondValue"}"""
        ),
        arrayOf(null, String.serializer(), null)
    )
    val provideStringTypeExpectedObject: Array<Array<Any?>> = arrayOf(
        arrayOf("\"simple string\"", String.serializer(), "simple string"),
        arrayOf("\"special {[]} chars\"", String.serializer(), "special {[]} chars"),
        arrayOf("42", Int.serializer(), 42),
        arrayOf(
            """{"first":"firstValue","second":"secondValue"}""",
            PairSerializer(String.serializer(), String.serializer()),
            "firstValue" to "secondValue"
        ),
        arrayOf(null, String.serializer(), null),
        arrayOf(null, Int.serializer(), null),
    )

    for (data in provideObjExpectedString) {
        test("serialize${data[0]}_serializesAsExpected") {
            @Suppress("UNCHECKED_CAST")
            val actual = deSerializer.serializeNullableToString(data[0], data[1] as KSerializer<Any>)
            assertEquals(actual, data[2])
        }
    }

    for (data in provideStringTypeExpectedObject) {
        test("deserialize${data[0]}_deserializesAsExpected") {
            @Suppress("UNCHECKED_CAST")
            val actual = deSerializer.deserialize(data[0] as String?, data[1] as KSerializer<Any>)
            assertEquals(actual, data[2])
        }
    }
}
