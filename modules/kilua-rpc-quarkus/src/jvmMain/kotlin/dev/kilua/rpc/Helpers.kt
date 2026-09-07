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

@file:Suppress("removal", "DeprecatedCallableAddReplaceWith")

package dev.kilua.rpc

import io.quarkus.vertx.http.runtime.CurrentVertxRequest
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.MultiMap
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.ServerWebSocket
import io.vertx.core.http.WebSocket
import io.vertx.core.http.WebSocketFrame
import io.vertx.core.net.HostAndPort
import io.vertx.core.net.SocketAddress
import io.vertx.ext.web.RoutingContext
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Alternative
import jakarta.enterprise.inject.Produces
import java.security.cert.Certificate
import javax.net.ssl.SSLSession
import javax.security.cert.X509Certificate

/**
 * Holds the current request RoutingContext available while an RPC service bean is being resolved.
 */
internal object RoutingContextHolder {
    val routingContext = ThreadLocal<RoutingContext>()
}

/**
 * Holds the current request ServerWebSocket available while an RPC service bean is being resolved.
 */
internal object ServerWebSocketHolder {
    val serverWebSocket = ThreadLocal<ServerWebSocket>()
}

/**
 * CDI producer of the current request [RoutingContext] and [ServerWebSocket].
 *
 * The [RoutingContext] producer is registered as an enabled [Alternative] to override the
 * request scoped producer provided by Quarkus. Quarkus' request scoped proxy resolves the
 * value lazily, which fails when the service is invoked on a coroutine dispatcher without
 * an active request context. This producer reads the context eagerly while the bean is being
 * resolved during [RpcServiceManager.getService]. For Quarkus internals (outside an RPC
 * invocation) it falls back to [CurrentVertxRequest.getCurrent].
 *
 * [ServerWebSocket] falls back to [DummyServerWebSocket] when not called over a WebSocket connection.
 */
@ApplicationScoped
@Alternative
@Priority(100)
public open class KiluaRpcContextProducer {
    @Produces
    public fun routingContext(currentVertxRequest: CurrentVertxRequest): RoutingContext =
        RoutingContextHolder.routingContext.get()
            ?: currentVertxRequest.current
            ?: throw IllegalStateException("RoutingContext is not available outside of an RPC request")

    @Produces
    public fun serverWebSocket(): ServerWebSocket =
        ServerWebSocketHolder.serverWebSocket.get() ?: DummyServerWebSocket()
}

/**
 * A placeholder implementation of [ServerWebSocket] for cases where the service is called
 * from HTTP or SSE and no WebSocket connection is available.
 * @suppress internal class
 */
public class DummyServerWebSocket : ServerWebSocket {
    override fun exceptionHandler(handler: Handler<Throwable?>?): ServerWebSocket? {
        throw IllegalStateException("Empty implementation")
    }

    override fun handler(handler: Handler<Buffer?>?): ServerWebSocket? {
        throw IllegalStateException("Empty implementation")
    }

    override fun pause(): ServerWebSocket? {
        throw IllegalStateException("Empty implementation")
    }

    override fun resume(): ServerWebSocket? {
        throw IllegalStateException("Empty implementation")
    }

    override fun fetch(amount: Long): ServerWebSocket? {
        throw IllegalStateException("Empty implementation")
    }

    override fun endHandler(endHandler: Handler<Void?>?): ServerWebSocket? {
        throw IllegalStateException("Empty implementation")
    }

    override fun setWriteQueueMaxSize(maxSize: Int): ServerWebSocket? {
        throw IllegalStateException("Empty implementation")
    }

    override fun drainHandler(handler: Handler<Void?>?): ServerWebSocket? {
        throw IllegalStateException("Empty implementation")
    }

    override fun closeHandler(handler: Handler<Void?>?): ServerWebSocket? {
        throw IllegalStateException("Empty implementation")
    }

    override fun frameHandler(handler: Handler<WebSocketFrame?>?): ServerWebSocket? {
        throw IllegalStateException("Empty implementation")
    }

    override fun scheme(): String? {
        throw IllegalStateException("Empty implementation")
    }

    @Deprecated("Deprecated in Java")
    override fun host(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun authority(): HostAndPort? {
        throw IllegalStateException("Empty implementation")
    }

    override fun uri(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun path(): String? {
        throw IllegalStateException("Empty implementation")
    }

    @Deprecated("Deprecated in Java")
    override fun query(): String? {
        throw IllegalStateException("Empty implementation")
    }

    @Deprecated("Deprecated in Java")
    override fun accept() {
        throw IllegalStateException("Empty implementation")
    }

    @Deprecated("Deprecated in Java")
    override fun reject() {
        throw IllegalStateException("Empty implementation")
    }

    @Deprecated("Deprecated in Java")
    override fun reject(statusCode: Int) {
        throw IllegalStateException("Empty implementation")
    }

    @Deprecated("Deprecated in Java")
    override fun setHandshake(handshake: Future<Int>?, handler: Handler<AsyncResult<Int>?>?) {
        throw IllegalStateException("Empty implementation")
    }

    @Deprecated("Deprecated in Java")
    override fun setHandshake(handshake: Future<Int>?): Future<Int>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun sslSession(): SSLSession? {
        throw IllegalStateException("Empty implementation")
    }

    override fun textMessageHandler(handler: Handler<String?>?): WebSocket? {
        throw IllegalStateException("Empty implementation")
    }

    override fun binaryMessageHandler(handler: Handler<Buffer?>?): WebSocket? {
        throw IllegalStateException("Empty implementation")
    }

    override fun pongHandler(handler: Handler<Buffer?>?): WebSocket? {
        throw IllegalStateException("Empty implementation")
    }

    override fun binaryHandlerID(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun textHandlerID(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun subProtocol(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun closeStatusCode(): Short? {
        throw IllegalStateException("Empty implementation")
    }

    override fun closeReason(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun headers(): MultiMap? {
        throw IllegalStateException("Empty implementation")
    }

    override fun writeFrame(frame: WebSocketFrame?): Future<Void?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun writeFrame(frame: WebSocketFrame?, handler: Handler<AsyncResult<Void>?>?): ServerWebSocket? {
        throw IllegalStateException("Empty implementation")
    }

    override fun writeFinalTextFrame(text: String?): Future<Void?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun writeFinalTextFrame(text: String?, handler: Handler<AsyncResult<Void>?>?): ServerWebSocket? {
        throw IllegalStateException("Empty implementation")
    }

    override fun writeFinalBinaryFrame(data: Buffer?): Future<Void?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun writeFinalBinaryFrame(data: Buffer?, handler: Handler<AsyncResult<Void>?>?): ServerWebSocket? {
        throw IllegalStateException("Empty implementation")
    }

    override fun writeBinaryMessage(data: Buffer?): Future<Void?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun writeBinaryMessage(data: Buffer?, handler: Handler<AsyncResult<Void>?>?): ServerWebSocket? {
        throw IllegalStateException("Empty implementation")
    }

    override fun writeTextMessage(text: String?): Future<Void?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun writeTextMessage(text: String?, handler: Handler<AsyncResult<Void>?>?): ServerWebSocket? {
        throw IllegalStateException("Empty implementation")
    }

    override fun writePing(data: Buffer?): Future<Void?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun writePing(data: Buffer?, handler: Handler<AsyncResult<Void>?>?): ServerWebSocket? {
        throw IllegalStateException("Empty implementation")
    }

    override fun writePong(data: Buffer?): Future<Void?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun writePong(data: Buffer?, handler: Handler<AsyncResult<Void>?>?): ServerWebSocket? {
        throw IllegalStateException("Empty implementation")
    }

    override fun end(): Future<Void?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun end(handler: Handler<AsyncResult<Void>?>?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun write(data: Buffer?): Future<Void?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun write(data: Buffer?, handler: Handler<AsyncResult<Void>?>?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun close(): Future<Void?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun close(handler: Handler<AsyncResult<Void>?>?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun close(statusCode: Short): Future<Void?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun close(statusCode: Short, handler: Handler<AsyncResult<Void>?>?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun close(statusCode: Short, reason: String?): Future<Void?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun close(
        statusCode: Short,
        reason: String?,
        handler: Handler<AsyncResult<Void>?>?
    ) {
        throw IllegalStateException("Empty implementation")
    }

    override fun remoteAddress(): SocketAddress? {
        throw IllegalStateException("Empty implementation")
    }

    override fun localAddress(): SocketAddress? {
        throw IllegalStateException("Empty implementation")
    }

    override fun isSsl(): Boolean {
        throw IllegalStateException("Empty implementation")
    }

    override fun isClosed(): Boolean {
        throw IllegalStateException("Empty implementation")
    }

    override fun peerCertificates(): List<Certificate?>? {
        throw IllegalStateException("Empty implementation")
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun peerCertificateChain(): Array<X509Certificate>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun writeQueueFull(): Boolean {
        throw IllegalStateException("Empty implementation")
    }

}