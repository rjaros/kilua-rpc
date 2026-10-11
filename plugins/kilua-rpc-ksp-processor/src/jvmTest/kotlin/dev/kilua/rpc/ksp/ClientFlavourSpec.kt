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

import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Which source set is generated for which client.
 *
 * A generated file is written once per target, and nothing but the name of the source set KSP put
 * it in says which target that was. Getting it wrong produces an empty file rather than an error,
 * so the mapping is pinned down here.
 */
val ClientFlavourSpec by testSuite {

    test("the browser targets are generated against the fetch based client") {
        for (name in listOf("jsMain", "wasmJsMain")) {
            assertEquals(ClientFlavour.WEB, ClientFlavour.of(name), name)
        }
    }

    test("jvm and android are generated against the Ktor client with a JVM facade") {
        for (name in listOf("jvmMain", "androidMain")) {
            assertEquals(ClientFlavour.KTOR_JVM, ClientFlavour.of(name), name)
        }
    }

    test("a native target is generated against the Ktor client but with no JVM facade") {
        // The one that matters for a new target: it is native, so it must not be handed a
        // `@file:JvmName`, which does not compile outside the JVM.
        for (name in listOf("linuxX64Main", "linuxArm64Main", "mingwX64Main")) {
            assertEquals(ClientFlavour.KTOR_NATIVE, ClientFlavour.of(name), name)
        }
        assertEquals(null, ClientFlavour.KTOR_NATIVE.fileJvmNameOf("PingServiceImpl"))
    }

    test("an intermediate native source set is recognised as well as a leaf") {
        // A leaf compilation is what KSP actually writes into, but the default hierarchy also builds
        // groups above the leaves, and a processor pointed at one of those must not go quiet.
        for (name in listOf("nativeMain", "linuxMain", "appleMain", "iosMain", "mingwMain")) {
            assertEquals(ClientFlavour.KTOR_NATIVE, ClientFlavour.of(name), name)
        }
    }

    test("a future native target needs no new entry") {
        // The list matches on family rather than on whole target names, which is what makes adding
        // one a non-event. This is the regression guard for that decision.
        for (name in listOf("iosArm64Main", "iosX64Main", "iosSimulatorArm64Main", "macosArm64Main")) {
            assertEquals(ClientFlavour.KTOR_NATIVE, ClientFlavour.of(name), name)
        }
    }

    test("a native client is never exported to JavaScript") {
        assertEquals(false, ClientFlavour.KTOR_NATIVE.honoursJsExport)
        assertEquals(true, ClientFlavour.WEB.honoursJsExport)
        assertEquals(false, ClientFlavour.KTOR_JVM.honoursJsExport)
    }

    test("a source set with no client of its own resolves to nothing") {
        for (name in listOf(null, "commonMain", "commonTest", "jvmTest", "", "Main")) {
            assertNull(ClientFlavour.of(name), "expected no flavour for $name")
        }
    }

    test("a jvm client keeps the facade name it has always had") {
        assertEquals("PingServiceImplClient", ClientFlavour.KTOR_JVM.fileJvmNameOf("PingServiceImpl"))
        assertEquals(null, ClientFlavour.WEB.fileJvmNameOf("PingServiceImpl"))
    }

    test("the generated client factory keeps the facade name it has always had") {
        assertEquals("GeneratedRpcServiceManagerFunctions", ClientFlavour.KTOR_JVM.managerFunctionsJvmName)
        assertNull(ClientFlavour.WEB.managerFunctionsJvmName)
        assertNull(ClientFlavour.KTOR_NATIVE.managerFunctionsJvmName)
    }

    test("every flavour but the browser names the same Ktor request type") {
        assertEquals(
            listOf("io.ktor.client.request.HttpRequestBuilder"),
            listOf(ClientFlavour.KTOR_JVM, ClientFlavour.KTOR_NATIVE).map { it.requestClass }.distinct()
        )
        assertNotNull(ClientFlavour.WEB.requestClass)
    }
}