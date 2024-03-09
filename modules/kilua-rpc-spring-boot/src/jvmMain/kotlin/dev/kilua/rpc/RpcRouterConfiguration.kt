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

import jakarta.annotation.PostConstruct
import kotlinx.serialization.modules.SerializersModule
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.MediaType.TEXT_HTML
import org.springframework.stereotype.Component
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.config.ResourceHandlerRegistry
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.BodyBuilder
import org.springframework.web.reactive.function.server.ServerResponse.HeadersBuilder
import org.springframework.web.reactive.function.server.buildAndAwait
import org.springframework.web.reactive.function.server.coRouter
import org.springframework.web.reactive.function.server.router
import java.util.*


/**
 * Default Spring Boot routes
 */
@Configuration
public open class RpcRouterConfiguration {
    @Value("classpath:/public/index.html")
    private lateinit var indexHtml: Resource

    @Bean
    public open fun rpcRoutes(rpcHandler: RpcHandler): RouterFunction<ServerResponse> = coRouter {
        GET("/rpc/**", rpcHandler::handle)
        POST("/rpc/**", rpcHandler::handle)
        PUT("/rpc/**", rpcHandler::handle)
        DELETE("/rpc/**", rpcHandler::handle)
        OPTIONS("/rpc/**", rpcHandler::handle)
        GET("/rpcsse/**", rpcHandler::handleSse)
    }

    @Bean
    public open fun indexRouter(): RouterFunction<ServerResponse> = router {
        GET("/") {
            ok().contentType(TEXT_HTML).bodyValue(indexHtml)
        }
    }
}

@Configuration
@EnableWebFlux
public open class WebFluxConfig : WebFluxConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val registration = registry.addResourceHandler("/**")
        registration.addResourceLocations("classpath:/public/")
        val wasmMediaType = MediaType.parseMediaType("application/wasm")
        registration.setMediaTypes(Collections.singletonMap("wasm", wasmMediaType))
    }

}

/**
 * Default Spring Boot handler
 */
@Component
public open class RpcHandler(
    private val services: List<RpcServiceManager<*>>,
    private val applicationContext: ApplicationContext
) {

    @Autowired(required = false)
    public val serializersModules: List<SerializersModule>? = null

    private val threadLocalRequest = ThreadLocal<ServerRequest>()

    private val threadLocalHeadersBuilder = ThreadLocal<HeadersBuilder<BodyBuilder>>()

    @PostConstruct
    public open fun init() {
        services.forEach { it.deSerializer = kotlinxObjectDeSerializer(serializersModules) }
    }

    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    public open fun serverRequest(): ServerRequest {
        return threadLocalRequest.get() ?: DummyServerRequest()
    }

    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    public open fun headersBuilder(): HeadersBuilder<BodyBuilder> {
        return threadLocalHeadersBuilder.get() ?: ServerResponse.ok()
    }

    public open suspend fun handle(request: ServerRequest): ServerResponse {

        fun getHandler(): RequestHandler? {
            val springMethod = request.method().name()
            val rpcMethod = HttpMethod.fromStringOrNull(springMethod) ?: return null
            val routeUrl = request.path()
            return services.asSequence()
                .mapNotNull { it.routeMapRegistry.findHandler(rpcMethod, routeUrl) }
                .firstOrNull()
        }

        return (getHandler() ?: return ServerResponse.notFound().buildAndAwait())(
            request,
            threadLocalRequest,
            threadLocalHeadersBuilder,
            applicationContext
        )
    }

    public open suspend fun handleSse(request: ServerRequest): ServerResponse {

        fun getSseHandler(): RequestHandler? {
            val routeUrl = request.path()
            return services.asSequence()
                .mapNotNull { it.sseRequests[routeUrl] }
                .firstOrNull()
        }

        return (getSseHandler() ?: return ServerResponse.notFound().buildAndAwait())(
            request,
            threadLocalRequest,
            threadLocalHeadersBuilder,
            applicationContext
        )
    }
}
