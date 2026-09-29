package dev.kilua.rpc

public fun getRpcUrlPrefix(): String {
    return System.getProperty("kilua.rpc.url")
        ?: error("URL must be provided for JVM client. Set it as parameter or use -Dkilua.rpc.url system property.")
}

public fun getWebSocketUrl(url: String): String {
    return httpToWebSocketUrl(url) ?: url
}
