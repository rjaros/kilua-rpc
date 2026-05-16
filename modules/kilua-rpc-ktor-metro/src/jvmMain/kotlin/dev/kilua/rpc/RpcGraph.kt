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

import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import io.ktor.server.application.*
import io.ktor.server.websocket.*
import kotlin.reflect.KClass

/**
 * A dependency graph extension for RPC services in Ktor with Metro.
 * This graph provides the necessary context and services for handling RPC requests.
 */
@GraphExtension(RpcAppScope::class)
public interface RpcGraph {
    public val application: Application
    public val applicationCall: ApplicationCall
    public val webSocketServerSession: WebSocketServerSession

    @Multibinds(allowEmpty = true)
    public val services: Map<KClass<*>, MetroRpcService>

    @GraphExtension.Factory
    public interface Factory {
        public fun create(
            @Provides application: Application,
            @Provides applicationCall: ApplicationCall,
            @Provides webSocketServerSession: WebSocketServerSession
        ): RpcGraph
    }
}
