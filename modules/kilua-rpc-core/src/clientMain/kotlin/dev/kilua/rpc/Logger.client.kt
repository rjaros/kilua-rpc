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

import io.ktor.client.plugins.logging.Logger

/**
 * The logger the agent reports failures through.
 *
 * The type is Ktor's own logger so that the client does not drag a logging facade of its own into
 * the ABI. What backs it is a per platform decision: the JVM default writes through slf4j, the
 * android one through `android.util.Log`, and anything else through the console.
 */
@PublishedApi
internal expect fun rpcLogger(): Logger

/**
 * Reports a failure, appending the stack trace to the message.
 *
 * Ktor's logger takes a single message and has no notion of a throwable, so the trace has to
 * travel as text. The alternative - an slf4j logger - is what this replaces, and the trace is
 * preserved either way.
 */
@PublishedApi
internal fun Logger.logError(message: String, throwable: Throwable? = null) {
    log(if (throwable == null) message else "$message\n$throwable")
}
