package dev.kilua.rpc

public fun getRpcUrlPrefix(): String {
    return System.getProperty("kilua.rpc.url")
        ?: error("URL must be provided for JVM client. Set it as parameter or use -Dkilua.rpc.url system property.")
}

public fun getWebSocketUrl(url: String): String {
    return if (url.startsWith("http://")) {
        "ws://${url.drop(7)}"
    } else if (url.startsWith("https://")) {
        "wss://${url.drop(8)}"
    } else {
        url
    }
}

/**
 * Returns the result of a JSON-RPC response or throws, following the same convention as
 * [dev.kilua.rpc.CallAgent.parseResponse]. Callers decoding a streaming payload must go through
 * this instead of dereferencing `result`, otherwise an error response surfaces as an NPE.
 */
@PublishedApi
internal fun JsonRpcResponse.requireResult(): String {
    val error = this.error
    if (error != null) {
        throw Exception(error)
    }
    return this.result ?: throw Exception("Invalid response")
}
