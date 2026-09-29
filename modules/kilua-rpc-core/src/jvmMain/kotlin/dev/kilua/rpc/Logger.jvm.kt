package dev.kilua.rpc

import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.Logger

@PublishedApi
internal actual fun rpcLogger(): Logger = Logger.DEFAULT
