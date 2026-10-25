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

import dev.kilua.rpc.types.Decimal
import dev.kilua.rpc.types.serializers.DecimalSerializer
import dev.kilua.rpc.types.toDecimal
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals

/**
 * What a native client does differently from every other flavour.
 *
 * The suites inherited from `commonTest` already run here, so nothing here repeats coverage that
 * the JVM or the browser gets for free. What is left is the behaviour that is genuinely native: a
 * decimal is a `Double` because the standard library has no arbitrary precision type to be, and the
 * url comes from the environment because a native build has no system properties.
 */
val NativeSpec by testSuite {

test("a decimal is a plain number on the wire") {
        // The one that matters most: a native client has to agree with a JVM server about the JSON
        // they exchange, and the JVM writes a BigDecimal where this writes a double, so both ends
        // seeing a bare JSON number is the property being pinned down.
        //
        // Note what does the work here. `Decimal` is a typealias for Double on this platform, so
        // this goes through kotlinx serialization's own Double serializer rather than through
        // `DecimalSerializer` - a typealias erases to the same runtime type and therefore cannot
        // select a different one. The native `RpcSerialization` registers no contextual entry for
        // it either, exactly as the browser half does not.
        assertEquals("1.5", RpcSerialization.plain.encodeToString(1.5.toDecimal()))
        assertEquals("0.0", RpcSerialization.plain.encodeToString(0.0.toDecimal()))
        assertEquals(1.5, RpcSerialization.plain.decodeFromString<Decimal>("1.5"))
    }

    test("the decimal serializer writes a double when it is the one used") {
        // Reached only by passing it explicitly, since the call above cannot reach it. Still worth
        // pinning: it is the serializer the JVM registers contextually for BigDecimal, so the two
        // halves have to agree on what a decimal looks like even though only one of them uses this
        // type by default.
        assertEquals("1.5", RpcSerialization.plain.encodeToString(DecimalSerializer, 1.5.toDecimal()))
        assertEquals("0.0", RpcSerialization.plain.encodeToString(DecimalSerializer, 0.0.toDecimal()))
        assertEquals(1.5, RpcSerialization.plain.decodeFromString(DecimalSerializer, "1.5"))
    }

    test("a decimal converts back to the primitive types it came from") {
        assertEquals(1.5, 1.5.toDecimal())
        assertEquals(3, 3.toDecimal().toInt())
    }

    test("an unset url leaves the caller to supply one") {
        // Nothing in a test run sets this, which is the situation the empty default exists for. The
        // alternative - failing the way the JVM half does - would make every native client that
        // was handed a base url depend on an environment variable it does not need.
        assertEquals("", getRpcUrlPrefix())
    }

    test("a websocket url is derived from its http scheme") {
        // Lives in `clientMain` now, and asserted here because the shared code is compiled for this
        // target and a Native-only regression would not be visible from the JVM suites.
        assertEquals("ws://host:8080/rpcws/route", getWebSocketUrl("http://host:8080/rpcws/route"))
        assertEquals("wss://host/rpcws/route", getWebSocketUrl("https://host/rpcws/route"))
        assertEquals("ws://host/rpcws/route", getWebSocketUrl("ws://host/rpcws/route"))
        assertEquals("wss://host/rpcws/route", getWebSocketUrl("wss://host/rpcws/route"))
    }
}