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
