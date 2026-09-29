package dev.kilua.rpc

import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.logging.Logger

/**
 * Backs the agent logger with `android.util.Log`, which is what a plain android application
 * expects to read. The slf4j based default is only reachable once the consumer adds an slf4j
 * binding, which android applications usually do not have.
 */
@PublishedApi
internal actual fun rpcLogger(): Logger = Logger.ANDROID
