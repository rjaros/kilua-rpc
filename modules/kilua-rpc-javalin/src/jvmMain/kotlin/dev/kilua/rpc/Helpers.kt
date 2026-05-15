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

import io.javalin.config.JavalinState
import io.javalin.config.Key
import io.javalin.config.MultipartConfig
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.javalin.http.servlet.JavalinServletContextConfig
import io.javalin.http.servlet.JavalinWsServletContext
import io.javalin.json.JsonMapper
import io.javalin.plugin.ContextPlugin
import io.javalin.router.Endpoint
import io.javalin.router.Endpoints
import io.javalin.security.RouteRole
import io.javalin.websocket.WsContext
import jakarta.servlet.AsyncContext
import jakarta.servlet.DispatcherType
import jakarta.servlet.RequestDispatcher
import jakarta.servlet.ServletConnection
import jakarta.servlet.ServletContext
import jakarta.servlet.ServletInputStream
import jakarta.servlet.ServletOutputStream
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import jakarta.servlet.http.HttpUpgradeHandler
import jakarta.servlet.http.Part
import org.eclipse.jetty.websocket.api.Callback
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.UpgradeRequest
import org.eclipse.jetty.websocket.api.UpgradeResponse
import org.eclipse.jetty.websocket.api.exceptions.WebSocketTimeoutException
import java.io.BufferedReader
import java.io.InputStream
import java.io.PrintWriter
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.security.Principal
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.stream.Stream

/**
 * @suppress internal class
 */
public class DummyContext : Context {
    override fun <T> appData(key: Key<T>): T {
        throw IllegalStateException("Empty implementation")
    }

    override fun future(future: Supplier<out CompletableFuture<*>>) {
        throw IllegalStateException("Empty implementation")
    }

    override fun jsonMapper(): JsonMapper {
        throw IllegalStateException("Empty implementation")
    }

    override fun minSizeForCompression(minSizeForCompression: Int): Context {
        throw IllegalStateException("Empty implementation")
    }

    override fun outputStream(): ServletOutputStream {
        throw IllegalStateException("Empty implementation")
    }

    override fun pathParam(key: String): String {
        throw IllegalStateException("Empty implementation")
    }

    override fun pathParamMap(): Map<String, String> {
        throw IllegalStateException("Empty implementation")
    }

    override fun redirect(location: String, status: HttpStatus) {
        throw IllegalStateException("Empty implementation")
    }

    override fun req(): HttpServletRequest {
        throw IllegalStateException("Empty implementation")
    }

    override fun res(): HttpServletResponse {
        throw IllegalStateException("Empty implementation")
    }

    override fun endpoints(): Endpoints {
        throw IllegalStateException("Empty implementation")
    }

    override fun endpoint(): Endpoint {
        throw IllegalStateException("Empty implementation")
    }

    override fun result(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun result(resultStream: InputStream): Context {
        throw IllegalStateException("Empty implementation")
    }

    override fun resultInputStream(): InputStream? {
        throw IllegalStateException("Empty implementation")
    }

    override fun routeRoles(): Set<RouteRole> {
        throw IllegalStateException("Empty implementation")
    }

    override fun skipRemainingHandlers(): Context {
        throw IllegalStateException("Empty implementation")
    }

    override fun strictContentTypes(): Boolean {
        throw IllegalStateException("Empty implementation")
    }

    override fun <T> with(clazz: Class<out ContextPlugin<*, T>>): T {
        throw IllegalStateException("Empty implementation")
    }

    override fun multipartConfig(): MultipartConfig {
        throw IllegalStateException("Empty implementation")
    }

    override fun writeJsonStream(stream: Stream<*>) {
        throw IllegalStateException("Empty implementation")
    }

}

/**
 * @suppress internal class
 */
public class DummyWsContext(javalinState: JavalinState) : WsContext(
    JavalinWsServletContext(
        JavalinServletContextConfig.of(javalinState),
        DummyHttpServletRequest(),
        DummyHttpServletResponse(),
    ).attach(DummySession())
)

/**
 * @suppress internal class
 */
public class DummySession : Session {

    override fun disconnect() {
        throw IllegalStateException("Empty implementation")
    }

    override fun getLocalSocketAddress(): SocketAddress? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getRemoteSocketAddress(): SocketAddress? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getProtocolVersion(): String {
        throw IllegalStateException("Empty implementation")
    }

    override fun getUpgradeResponse(): UpgradeResponse {
        throw IllegalStateException("Empty implementation")
    }

    override fun getUpgradeRequest(): UpgradeRequest {
        throw IllegalStateException("Empty implementation")
    }

    override fun isOpen(): Boolean {
        throw IllegalStateException("Empty implementation")
    }

    override fun getIdleTimeout(): Duration {
        throw IllegalStateException("Empty implementation")
    }

    override fun getInputBufferSize(): Int {
        throw IllegalStateException("Empty implementation")
    }

    override fun getOutputBufferSize(): Int {
        throw IllegalStateException("Empty implementation")
    }

    override fun getMaxBinaryMessageSize(): Long {
        throw IllegalStateException("Empty implementation")
    }

    override fun getMaxTextMessageSize(): Long {
        throw IllegalStateException("Empty implementation")
    }

    override fun getMaxFrameSize(): Long {
        throw IllegalStateException("Empty implementation")
    }

    override fun isAutoFragment(): Boolean {
        throw IllegalStateException("Empty implementation")
    }

    override fun setIdleTimeout(duration: Duration?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun setInputBufferSize(size: Int) {
        throw IllegalStateException("Empty implementation")
    }

    override fun setOutputBufferSize(size: Int) {
        throw IllegalStateException("Empty implementation")
    }

    override fun setMaxBinaryMessageSize(size: Long) {
        throw IllegalStateException("Empty implementation")
    }

    override fun setMaxTextMessageSize(size: Long) {
        throw IllegalStateException("Empty implementation")
    }

    override fun setMaxFrameSize(maxFrameSize: Long) {
        throw IllegalStateException("Empty implementation")
    }

    override fun setAutoFragment(autoFragment: Boolean) {
        throw IllegalStateException("Empty implementation")
    }

    override fun getMaxOutgoingFrames(): Int {
        throw IllegalStateException("Empty implementation")
    }

    override fun setMaxOutgoingFrames(maxOutgoingFrames: Int) {
        throw IllegalStateException("Empty implementation")
    }

    override fun demand() {
        throw IllegalStateException("Empty implementation")
    }

    override fun sendBinary(buffer: ByteBuffer?, callback: Callback?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun sendPartialBinary(
        buffer: ByteBuffer?,
        last: Boolean,
        callback: Callback?
    ) {
        throw IllegalStateException("Empty implementation")
    }

    override fun sendText(text: String?, callback: Callback?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun sendPartialText(
        text: String?,
        last: Boolean,
        callback: Callback?
    ) {
        throw IllegalStateException("Empty implementation")
    }

    override fun sendPing(applicationData: ByteBuffer?, callback: Callback?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun sendPong(applicationData: ByteBuffer?, callback: Callback?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun close() {
        throw IllegalStateException("Empty implementation")
    }

    override fun close(
        statusCode: Int,
        reason: String?,
        callback: Callback?
    ) {
        throw IllegalStateException("Empty implementation")
    }

    override fun isSecure(): Boolean {
        throw IllegalStateException("Empty implementation")
    }

    override fun addIdleTimeoutListener(onIdleTimeout: Predicate<WebSocketTimeoutException?>?) {
        throw IllegalStateException("Empty implementation")
    }

}

/**
 * @suppress internal class
 */
public class DummyHttpServletRequest : HttpServletRequest {
    override fun getAuthType(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getCookies(): Array<out Cookie?>? {
        return null
    }

    override fun getDateHeader(name: String?): Long {
        throw IllegalStateException("Empty implementation")
    }

    override fun getHeader(name: String?): String? {
        return null
    }

    override fun getHeaders(name: String?): Enumeration<String?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getHeaderNames(): Enumeration<String?> {
        return Collections.emptyEnumeration()
    }

    override fun getIntHeader(name: String?): Int {
        throw IllegalStateException("Empty implementation")
    }

    override fun getMethod(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getPathInfo(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getPathTranslated(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getContextPath(): String {
        return "/"
    }

    override fun getQueryString(): String? {
        return null
    }

    override fun getRemoteUser(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun isUserInRole(role: String?): Boolean {
        throw IllegalStateException("Empty implementation")
    }

    override fun getUserPrincipal(): Principal? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getRequestedSessionId(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getRequestURI(): String {
        return ""
    }

    override fun getRequestURL(): StringBuffer? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getServletPath(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getSession(create: Boolean): HttpSession? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getSession(): HttpSession {
        return DummyHttpSession()
    }

    override fun changeSessionId(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun isRequestedSessionIdValid(): Boolean {
        throw IllegalStateException("Empty implementation")
    }

    override fun isRequestedSessionIdFromCookie(): Boolean {
        throw IllegalStateException("Empty implementation")
    }

    override fun isRequestedSessionIdFromURL(): Boolean {
        throw IllegalStateException("Empty implementation")
    }

    override fun authenticate(response: HttpServletResponse?): Boolean {
        throw IllegalStateException("Empty implementation")
    }

    override fun login(username: String?, password: String?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun logout() {
        throw IllegalStateException("Empty implementation")
    }

    override fun getParts(): Collection<Part?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getPart(name: String?): Part? {
        throw IllegalStateException("Empty implementation")
    }

    override fun <T : HttpUpgradeHandler?> upgrade(handlerClass: Class<T?>?): T? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getAttribute(name: String?): Any? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getAttributeNames(): Enumeration<String?>? {
        return Collections.emptyEnumeration()
    }

    override fun getCharacterEncoding(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun setCharacterEncoding(env: String?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun getContentLength(): Int {
        throw IllegalStateException("Empty implementation")
    }

    override fun getContentLengthLong(): Long {
        throw IllegalStateException("Empty implementation")
    }

    override fun getContentType(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getInputStream(): ServletInputStream? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getParameter(name: String?): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getParameterNames(): Enumeration<String?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getParameterValues(name: String?): Array<out String?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getParameterMap(): Map<String?, Array<out String?>?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getProtocol(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getScheme(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getServerName(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getServerPort(): Int {
        throw IllegalStateException("Empty implementation")
    }

    override fun getReader(): BufferedReader? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getRemoteAddr(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getRemoteHost(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun setAttribute(name: String?, o: Any?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun removeAttribute(name: String?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun getLocale(): Locale? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getLocales(): Enumeration<Locale?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun isSecure(): Boolean {
        throw IllegalStateException("Empty implementation")
    }

    override fun getRequestDispatcher(path: String?): RequestDispatcher? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getRemotePort(): Int {
        throw IllegalStateException("Empty implementation")
    }

    override fun getLocalName(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getLocalAddr(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getLocalPort(): Int {
        throw IllegalStateException("Empty implementation")
    }

    override fun getServletContext(): ServletContext? {
        throw IllegalStateException("Empty implementation")
    }

    override fun startAsync(): AsyncContext? {
        throw IllegalStateException("Empty implementation")
    }

    override fun startAsync(
        servletRequest: ServletRequest?,
        servletResponse: ServletResponse?
    ): AsyncContext? {
        throw IllegalStateException("Empty implementation")
    }

    override fun isAsyncStarted(): Boolean {
        throw IllegalStateException("Empty implementation")
    }

    override fun isAsyncSupported(): Boolean {
        throw IllegalStateException("Empty implementation")
    }

    override fun getAsyncContext(): AsyncContext? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getDispatcherType(): DispatcherType? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getRequestId(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getProtocolRequestId(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getServletConnection(): ServletConnection? {
        throw IllegalStateException("Empty implementation")
    }

}

/**
 * @suppress internal class
 */
public class DummyHttpServletResponse : HttpServletResponse {
    override fun addCookie(cookie: Cookie?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun containsHeader(name: String?): Boolean {
        throw IllegalStateException("Empty implementation")
    }

    override fun encodeURL(url: String?): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun encodeRedirectURL(url: String?): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun sendError(sc: Int, msg: String?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun sendError(sc: Int) {
        throw IllegalStateException("Empty implementation")
    }

    override fun sendRedirect(location: String?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun setDateHeader(name: String?, date: Long) {
        throw IllegalStateException("Empty implementation")
    }

    override fun addDateHeader(name: String?, date: Long) {
        throw IllegalStateException("Empty implementation")
    }

    override fun setHeader(name: String?, value: String?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun addHeader(name: String?, value: String?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun setIntHeader(name: String?, value: Int) {
        throw IllegalStateException("Empty implementation")
    }

    override fun addIntHeader(name: String?, value: Int) {
        throw IllegalStateException("Empty implementation")
    }

    override fun setStatus(sc: Int) {
        throw IllegalStateException("Empty implementation")
    }

    override fun getStatus(): Int {
        throw IllegalStateException("Empty implementation")
    }

    override fun getHeader(name: String?): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getHeaders(name: String?): Collection<String?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getHeaderNames(): Collection<String?>? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getCharacterEncoding(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getContentType(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getOutputStream(): ServletOutputStream? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getWriter(): PrintWriter? {
        throw IllegalStateException("Empty implementation")
    }

    override fun setCharacterEncoding(charset: String?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun setContentLength(len: Int) {
        throw IllegalStateException("Empty implementation")
    }

    override fun setContentLengthLong(len: Long) {
        throw IllegalStateException("Empty implementation")
    }

    override fun setContentType(type: String?) {
    }

    override fun setBufferSize(size: Int) {
        throw IllegalStateException("Empty implementation")
    }

    override fun getBufferSize(): Int {
        throw IllegalStateException("Empty implementation")
    }

    override fun flushBuffer() {
        throw IllegalStateException("Empty implementation")
    }

    override fun resetBuffer() {
        throw IllegalStateException("Empty implementation")
    }

    override fun isCommitted(): Boolean {
        throw IllegalStateException("Empty implementation")
    }

    override fun reset() {
        throw IllegalStateException("Empty implementation")
    }

    override fun setLocale(loc: Locale?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun getLocale(): Locale? {
        throw IllegalStateException("Empty implementation")
    }
}

public class DummyHttpSession : HttpSession {
    override fun getCreationTime(): Long {
        throw IllegalStateException("Empty implementation")
    }

    override fun getId(): String? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getLastAccessedTime(): Long {
        throw IllegalStateException("Empty implementation")
    }

    override fun getServletContext(): ServletContext? {
        throw IllegalStateException("Empty implementation")
    }

    override fun setMaxInactiveInterval(interval: Int) {
        throw IllegalStateException("Empty implementation")
    }

    override fun getMaxInactiveInterval(): Int {
        throw IllegalStateException("Empty implementation")
    }

    override fun getAttribute(name: String?): Any? {
        throw IllegalStateException("Empty implementation")
    }

    override fun getAttributeNames(): Enumeration<String?>? {
        return Collections.emptyEnumeration()
    }

    override fun setAttribute(name: String?, value: Any?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun removeAttribute(name: String?) {
        throw IllegalStateException("Empty implementation")
    }

    override fun invalidate() {
        throw IllegalStateException("Empty implementation")
    }

    override fun isNew(): Boolean {
        throw IllegalStateException("Empty implementation")
    }

}