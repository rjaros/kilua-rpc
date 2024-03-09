# Kilua RPC

Fullstack RPC library for Kotlin/Wasm and Kotlin/JS.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Kilua RPC is a powerful Remote Procedure Call (RPC) library designed for fullstack applications 
created with the Kotlin programming language. Is can be used with frontend apps developed for 
[Kotlin/Wasm](https://kotlinlang.org/docs/wasm-overview.html) and [Kotlin/JS](https://kotlinlang.org/docs/js-overview.html) targets. On the backend side different popular Kotlin/JVM web frameworks 
are fully supported:

- [Ktor](https://ktor.io/) (with [Koin](https://insert-koin.io/) or [Guice](https://github.com/google/guice) dependency injection)
- [Jooby](https://jooby.io)
- [Spring Boot](https://spring.io/projects/spring-boot)
- [Javalin](https://javalin.io)
- [Vert.x](https://vertx.io)
- [Micronaut](https://micronaut.io)

Kilua RPC is an independent project, but it is mostly based on stable and production ready 
fullstack interfaces implemented in the [KVision](https://kvision.io) framework. 

Kilua RPC can be used with all Kotlin/JS and Kotlin/Wasm web frameworks to easily build fullstack 
applications with shared code for data model and business logic.

## Features

- Compile the same application code for Kotlin/Wasm and Kotlin/JS targets 
with support for shared sources set.
- Support for serializable [kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime) types.
- Built-in `Decimal` type automatically mapped to `Double` in the 
browser and to `BigDecimal` on the server. 
- Support for `Result<T>` as a return type of remote methods.
- Automatic exceptions propagation from the server to the client, 
including support for custom exception types.
- Support for two-way communication with [WebSockets](https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API).
- Support for [SSE (Server-Sent Events)](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events).
- Automatic endpoints generation configurable with simple annotations.
- Gradle plugin for easy project setup and packaging.

## Code overview

This is a short overview of how to use Kilua RPC. It contains just the basic concepts and ideas.

### Common source set

Declare an annotated interface with one or more suspending methods.

```kotlin
import dev.kilua.rpc.annotations.RpcService
import kotlinx.serialization.Serializable

@Serializable
enum class EncodingType {
    BASE64, URLENCODE, HEX
}

@RpcService
interface IEncodingService {
    suspend fun encode(input: String, encodingType: EncodingType): String
}
```
### Frontend source set

Just use the class instance provided by the library calling its methods directly.

```kotlin
import dev.kilua.rpc.getService

val service = getService<IEncodingService>()
launch {
    val result: String = service.encode("Lorem ipsum", EncodingType.BASE64)
    // do something with the result
}
```
### Backend source set (Ktor)

Implement the interface as a DI component and initialize the library routing with a few lines of code.

```kotlin
import java.net.URLEncoder
import acme.Base64Encoder
import acme.HexEncoder
import dev.kilua.rpc.applyRoutes
import dev.kilua.rpc.getAllServiceManagers
import dev.kilua.rpc.initRpc
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.routing.*

actual class EncodingService : IEncodingService {
    override suspend fun encode(input: String, encodingType: EncodingType): String {
        return when (encodingType) {
                EncodingType.BASE64 -> {
                    Base64Encoder.encode(input)
                }
                EncodingType.URLENCODE -> {
                    URLEncoder.encode(input, "UTF-8")
                }
                EncodingType.HEX -> {
                    HexEncoder.encode(input)
                }
        }
    }
}

fun Application.main() {
    install(Compression)
    routing {
        getAllServiceManagers().forEach { applyRoutes(it) }
    }
    initRpc()
}
```
Everything else happens automatically - a call on the client side will run the code on the server and the result will be sent back to the caller.

## Examples

Each application server is configured a bit differently. You can check
the `examples` directory for simple applications created with all supported servers. 
You will notice the frontend applications are identical - the only real difference 
is the backend app dependencies list and the main initialization code. 

If you need more advanced information, until better documentation is ready, you can currently look into 
the [KVision fullstack development guide](https://kvision.gitbook.io/kvision-guide/6.-full-stack-development-guide), 
because the general Kilua RPC rules are the same as KVision. 

## Leave a star

If you like this project, please give it a star on GitHub. Thank you!
