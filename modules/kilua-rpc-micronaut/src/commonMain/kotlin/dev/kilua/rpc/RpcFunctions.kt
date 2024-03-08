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

package dev.kilua.rpc

import kotlin.reflect.KClass

/**
 * This will make IntelliJ think that this function exists.
 * The real implementation will be generated by the Kilua RPC Gradle Plugin.
 */
public inline fun <reified T : Any> getServiceManager(): RpcServiceManager<T> {
    throw NotImplementedError("Kilua RPC didn't generate code for " + T::class.simpleName + ". You need to apply Kilua RPC Gradle Plugin.")
}

/**
 * This will make IntelliJ think that this function exists.
 * The real implementation will be generated by the Kilua RPC Gradle Plugin.
 */
public fun getAllServiceManagers(): List<RpcServiceManager<*>> {
    throw NotImplementedError("Kilua RPC didn't generate code. You need to apply Kilua RPC Gradle Plugin.")
}

/**
 * This will make IntelliJ think that this function exists.
 * The real implementation will be generated by the Kilua RPC Gradle Plugin.
 */
@Suppress("UNUSED_PARAMETER")
public fun getServiceManagers(vararg kclass: KClass<*>): List<RpcServiceManager<*>> {
    throw NotImplementedError("Kilua RPC didn't generate code. You need to apply Kilua RPC Gradle Plugin.")
}