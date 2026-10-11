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
 * Which client a generated half is written for.
 *
 * A project has one compilation per target, and each of them gets its own generated file, so the
 * only thing that tells them apart is the name of the source set KSP wrote it into. Deciding what
 * to emit from that name is the only reason this type exists.
 */
internal enum class ClientFlavour(
    /** The type a request filter parameter is declared in. */
    val requestClass: String,
    /** Whether the interface's own `@JsExport` is carried into the generated client. */
    val honoursJsExport: Boolean,
    /**
     * Whether the generated file is given a JVM facade name. `@file:JvmName` does not compile
     * outside the JVM, so a native client gets none even though it uses the very same transport.
     */
    val hasJvmFacade: Boolean,
    /**
     * The JVM facade name of the generated client factory, or none outside the JVM.
     *
     * Named rather than derived, because it does not follow from the flavour's other properties: it
     * is a fixed word for the generated file, not something built out of a service's own name.
     */
    val managerFunctionsJvmName: String?
) {
    /** The browser, which talks to a server over `fetch` rather than over Ktor. */
    WEB(
        requestClass = "web.http.Request",
        honoursJsExport = true,
        hasJvmFacade = false,
        managerFunctionsJvmName = null
    ),

    /** JVM and Android, which share one Ktor client implementation. */
    KTOR_JVM(
        requestClass = "io.ktor.client.request.HttpRequestBuilder",
        honoursJsExport = false,
        hasJvmFacade = true,
        managerFunctionsJvmName = "GeneratedRpcServiceManagerFunctions"
    ),

    /** Kotlin/Native, which shares the Ktor client implementation with the JVM. */
    KTOR_NATIVE(
        requestClass = "io.ktor.client.request.HttpRequestBuilder",
        honoursJsExport = false,
        hasJvmFacade = false,
        managerFunctionsJvmName = null
    );

    /** The file facade name a generated client of `className` gets, or none outside the JVM. */
    fun fileJvmNameOf(className: String): String? = if (hasJvmFacade) "${className}Client" else null

    companion object {

        private val WEB_SOURCE_SETS = setOf("jsMain", "wasmJsMain")

        private val KTOR_JVM_SOURCE_SETS = setOf("jvmMain", "androidMain")

        /**
         * The leading part of every Kotlin/Native target and of every intermediate source set the
         * default hierarchy template builds above them.
         *
         * Matching on the family rather than on whole target names is what lets a new native target
         * work without touching this file: adding `linuxRiscV64` or `iosArm64` needs no new entry,
         * whereas listing every leaf would mean editing this list on every Kotlin upgrade.
         */
        private val NATIVE_FAMILIES = listOf(
            "native", "linux", "mingw", "apple", "macos", "ios", "tvos", "watchos", "androidNative"
        )

        /** The flavour [sourceSet] is generated for, or null when it has no client of its own. */
        fun of(sourceSet: String?): ClientFlavour? = when {
            sourceSet == null -> null
            sourceSet in WEB_SOURCE_SETS -> WEB
            sourceSet in KTOR_JVM_SOURCE_SETS -> KTOR_JVM
            isNative(sourceSet) -> KTOR_NATIVE
            else -> null
        }

        private fun isNative(sourceSet: String): Boolean {
            val name = sourceSet.removeSuffix("Main")
            return NATIVE_FAMILIES.any { name.startsWith(it) }
        }
    }
}