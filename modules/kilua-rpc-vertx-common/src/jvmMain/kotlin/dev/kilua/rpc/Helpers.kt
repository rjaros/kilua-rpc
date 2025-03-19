/*
 * Copyright (c) 2025 Robert Jaros
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

import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.MultiMap
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.http.ServerWebSocket
import io.vertx.core.http.WebSocket
import io.vertx.core.http.WebSocketFrame
import io.vertx.core.net.HostAndPort
import io.vertx.core.net.SocketAddress
import io.vertx.ext.web.FileUpload
import io.vertx.ext.web.ParsedHeaderValues
import io.vertx.ext.web.RequestBody
import io.vertx.ext.web.Route
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.Session
import io.vertx.ext.web.UserContext
import java.nio.charset.Charset
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSession

/**
 * @suppress internal class
 */
public class DummyRoutingContext : RoutingContext {
    override fun request(): HttpServerRequest? {
        throw IllegalStateException("Empty implementation")
    }

    override fun response(): HttpServerResponse? {
        throw IllegalStateException("Empty implementation")
    }

    override fun next() {
        throw IllegalStateException("Empty implementation")
    }

    override fun fail(statusCode: Int) {
        throw IllegalStateException("Empty implementation")
    }

    override fun fail(throwable: Throwable?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun fail(statusCode: Int, throwable: Throwable?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun put(key: String?, obj: Any?): RoutingContext? {
        throw IllegalStateException("Empty implementation")
    }

    override fun <T : Any?> get(key: String?): T? {
        throw IllegalStateException("Empty implementation")
    }

    override fun <T : Any?> get(key: String?, defaultValue: T?): T? {
        throw IllegalStateException("Empty implementation")
    }

    override fun <T : Any?> remove(key: String?): T? {
        throw IllegalStateException("Empty implementation")
    }

    override fun <T : Any?> data(): Map<String?, T?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun vertx(): Vertx? {
        throw IllegalStateException("Empty implementation")
    }

    override fun mountPoint(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun currentRoute(): Route? {
        throw IllegalStateException("Empty implementation")
    }

    override fun normalizedPath(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun body(): RequestBody? {
        throw IllegalStateException("Empty implementation")
    }

    override fun fileUploads(): List<FileUpload?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun cancelAndCleanupFileUploads() {
        throw IllegalStateException("Empty implementation")
    }

    override fun session(): Session? {
        throw IllegalStateException("Empty implementation")
    }

    override fun isSessionAccessed(): Boolean {
        throw IllegalStateException("Empty implementation")
    }

    override fun userContext(): UserContext? {
        throw IllegalStateException("Empty implementation")
    }

    override fun failure(): Throwable? {
        throw IllegalStateException("Empty implementation")
    }

    override fun statusCode(): Int {
        throw IllegalStateException("Empty implementation")
    }

    override fun getAcceptableContentType(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun parsedHeaders(): ParsedHeaderValues? {
        throw IllegalStateException("Empty implementation")
    }

    override fun addHeadersEndHandler(handler: Handler<Void?>?): Int {
        throw IllegalStateException("Empty implementation")
    }

    override fun removeHeadersEndHandler(handlerID: Int): Boolean {
        throw IllegalStateException("Empty implementation")
    }

    override fun addBodyEndHandler(handler: Handler<Void?>?): Int {
        throw IllegalStateException("Empty implementation")
    }

    override fun removeBodyEndHandler(handlerID: Int): Boolean {
        throw IllegalStateException("Empty implementation")
    }

    override fun addEndHandler(handler: Handler<AsyncResult<Void?>?>?): Int {
        throw IllegalStateException("Empty implementation")
    }

    override fun removeEndHandler(handlerID: Int): Boolean {
        throw IllegalStateException("Empty implementation")
    }

    override fun failed(): Boolean {
        throw IllegalStateException("Empty implementation")
    }

    override fun setAcceptableContentType(contentType: String?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun reroute(method: HttpMethod?, path: String?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun pathParams(): Map<String?, String?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun pathParam(name: String?): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun queryParams(): MultiMap? {
        throw IllegalStateException("Empty implementation")
    }

    override fun queryParams(encoding: Charset?): MultiMap? {
        throw IllegalStateException("Empty implementation")
    }

    override fun queryParam(name: String?): List<String?>? {
        throw IllegalStateException("Empty implementation")
    }
}

/**
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

    override fun authority(): HostAndPort? {
        throw IllegalStateException("Empty implementation")
    }

    override fun uri(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun path(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun query(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun sslSession(): SSLSession? {
        throw IllegalStateException("Empty implementation")
    }

    override fun shutdownHandler(handler: Handler<Void?>?): WebSocket? {
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

    override fun writeFinalTextFrame(text: String?): Future<Void?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun writeFinalBinaryFrame(data: Buffer?): Future<Void?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun writeBinaryMessage(data: Buffer?): Future<Void?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun writeTextMessage(text: String?): Future<Void?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun writePing(data: Buffer?): Future<Void?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun writePong(data: Buffer?): Future<Void?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun end(): Future<Void?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun shutdown(
        timeout: Long,
        unit: TimeUnit?,
        statusCode: Short,
        reason: String?
    ): Future<Void?>? {
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

    override fun write(data: Buffer?): Future<Void?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun writeQueueFull(): Boolean {
        throw IllegalStateException("Empty implementation")
    }

}
