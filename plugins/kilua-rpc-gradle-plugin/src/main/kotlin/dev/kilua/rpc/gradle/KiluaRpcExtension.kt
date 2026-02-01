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

package dev.kilua.rpc.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject


/**
 * Configuration for the Kilua RPC plugin.
 *
 * The default values are set in [KiluaRpcPlugin.createKiluaRpcExtension].
 */
public abstract class KiluaRpcExtension @Inject constructor(
    private val objects: ObjectFactory,
    private val providers: ProviderFactory,
) {

    public val enableGradleTasks: Property<Boolean> = kiluaRpcGradleProperty("enableGradleTasks", true)

    public val jarArchiveFileName: Property<String> = kiluaRpcGradleProperty("jarArchiveFileName")

    private fun kiluaRpcGradleProperty(
        property: String,
        default: Boolean,
    ): Property<Boolean> {
        val convention = providers.gradleProperty("dev.kilua.rpc.plugin.$property")
            .map { it.toBoolean() }
            .orElse(default)
        return objects.property(Boolean::class.java).convention(convention)
    }

    private fun kiluaRpcGradleProperty(
        property: String,
    ): Property<String> {
        val convention = providers.gradleProperty("dev.kilua.rpc.plugin.$property")
        return objects.property(String::class.java).convention(convention)
    }
}
