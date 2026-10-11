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

package dev.kilua.rpc.ksp

/**
 * How a remote method is transported. It is derived from the resolved types of the declaration
 * rather than from its rendered text, so a user type that merely shares a name with one of the
 * recognised ones is never mistaken for it.
 */
internal enum class RpcMethodKind {
    /** A plain request/response call. */
    CALL,

    /** A call returning a paginated `RemoteData` slab. */
    REMOTE_DATA,

    /** A two way call over WebSockets. */
    WEBSOCKET,

    /** A one way stream of server sent events. */
    SSE;

    /**
     * True for the kinds emitted through a channel branch, which covers both the raw declaration
     * and the handler overload the convention asks for.
     */
    val isChannel: Boolean get() = this == WEBSOCKET || this == SSE
}

/** A parameter as it is written into generated code, already rendered to text. */
internal data class RenderParameter(val name: String, val type: String)

/**
 * Everything the renderers need about a single declared method, gathered by the processor before
 * anything is written out.
 */
internal data class FunctionInfo(
    val name: String,
    val kind: RpcMethodKind,
    val parameters: List<RenderParameter>,
    val returnType: String?,
    val httpMethod: String,
    val route: String?,
    /**
     * The parameter types with their channel direction flipped, which is empty for the kinds that
     * are not streamed. Flipping needs the resolved declaration, so it happens where the types are
     * still available rather than in the renderer.
     */
    val flippedParameters: List<String>
)

/**
 * An annotated service interface, reduced to plain data.
 *
 * This is the boundary between the two halves of the generator: the processor resolves KSP symbols
 * and produces one of these, and [RpcCodeRenderer] turns it back into text. Keeping them apart is
 * what lets the rendering - which is where the awkward string handling lives - be tested without a
 * KSP resolver on the classpath.
 *
 * Which route a method is served by is decided before this point, in the processor, so it arrives
 * already resolved in [FunctionInfo.route].
 *
 * It carries no notion of being exported: that is the caller's to decide, because it is a property
 * of the flavour rather than of the interface.
 */
internal data class InterfaceInfo(
    val packageName: String,
    val interfaceName: String,
    val className: String,
    val managerName: String,
    val functions: List<FunctionInfo>,
    /**
     * Fully qualified names of the types the client override signatures need imported, walked from
     * the declared parameters. The manager needs none of these: its body names no parameter type.
     */
    val types: Set<String>
)

/**
 * Turns [InterfaceInfo] into the Kotlin source the generator is defined by.
 *
 * Everything in here is pure string handling: no KSP types are read, so any of it can be exercised
 * by handing over a value built by hand.
 */
internal object RpcCodeRenderer {

    /** The service manager object: what binds every method of an interface to a route. */
    fun renderManager(info: InterfaceInfo): String {
        // The manager body never names a parameter or return type, only the same-package interface
        // and the fixed dev.kilua.rpc imports, so there are no collected types to import here.
        return StringBuilder().apply {
            appendGeneratedHeader()
            appendLine("package ${info.packageName}")
            appendLine()
            appendLine("import dev.kilua.rpc.HttpMethod")
            appendLine("import dev.kilua.rpc.RpcServiceManager")
            appendLine("import dev.kilua.rpc.registerRpcServiceExceptions")
            appendLine()
            appendLine("object ${info.managerName} : RpcServiceManager<${info.interfaceName}>(${info.interfaceName}::class) {")
            appendLine("    init {")
            appendLine("        registerRpcServiceExceptions()")
            emitManagerBinds(info)
            appendLine("    }")
            appendLine("}")
        }.toString()
    }

    /**
     * The client implementation: one override per declared method.
     *
     * [isExported] is a property of the flavour rather than of the interface, because the annotation
     * it mirrors lives in the Kotlin/JS standard library and only compiles there. The processor
     * passes false for every flavour but the web ones, which is why a `@JsExport` interface still
     * produces plain JVM and Android clients.
     */
    fun renderClient(
        info: InterfaceInfo,
        requestClass: String,
        fileJvmName: String?,
        isExported: Boolean
    ): String {
        val requestClassName = requestClassNameOf(requestClass)
        // A name that also has a channel overload is served by the override the channel branch
        // emits, so the plain one must not be emitted as well.
        val channelNames = info.functions.filter { it.kind.isChannel }.map { it.name }.toSet()
        return StringBuilder().apply {
            appendGeneratedHeader()
            fileJvmName?.let { appendLine("@file:JvmName(\"$it\")") }
            appendLine("package ${info.packageName}")
            appendLine()
            appendLine("import $requestClass")
            appendLine("import dev.kilua.rpc.RpcAgent")
            if (isExported) {
                appendLine("import kotlin.js.ExperimentalJsExport")
                appendLine("import kotlin.js.JsExport")
            }
            appendLine("import kotlinx.serialization.modules.SerializersModule")
            appendImports(info.types)
            appendLine()
            if (isExported) {
                appendLine("@OptIn(ExperimentalJsExport::class)")
                appendLine("@JsExport")
            }
            appendLine(
                "class ${info.className}(baseUrl: String? = null, " +
                    "serializersModules: List<SerializersModule>? = null, " +
                    "requestFilter: (suspend $requestClassName.() -> Unit)? = null) : " +
                    "${info.interfaceName}, RpcAgent<${info.interfaceName}>(baseUrl, " +
                    "${info.managerName}, serializersModules, requestFilter) {"
            )
            info.functions.forEach { function ->
                val name = function.name
                when (function.kind) {
                    RpcMethodKind.CALL, RpcMethodKind.REMOTE_DATA ->
                        if (name !in channelNames) {
                            if (function.parameters.isEmpty()) {
                                appendLine("    override suspend fun $name() = call(${info.interfaceName}::$name)")
                            } else {
                                appendLine(
                                    "    override suspend fun $name(${parameterList(function)}) = " +
                                        "call(${info.interfaceName}::$name, ${parameterNames(function)})"
                                )
                            }
                        }

                    RpcMethodKind.WEBSOCKET -> {
                        appendLine("    override suspend fun $name(${parameterList(function)}) {}")
                        // The handler runs with the channels the other way round from the declaration.
                        val (outgoing, incoming) = function.flippedParameters
                        appendLine(
                            "    override suspend fun $name(handler: suspend ($outgoing, $incoming) -> Unit) = " +
                                "webSocket(${info.interfaceName}::$name, handler)"
                        )
                    }

                    RpcMethodKind.SSE -> {
                        appendLine("    override suspend fun $name(${parameterList(function)}) {}")
                        val (incoming) = function.flippedParameters
                        appendLine(
                            "    override suspend fun $name(handler: suspend ($incoming) -> Unit) = " +
                                "sseConnection(${info.interfaceName}::$name, handler)"
                        )
                    }
                }
            }
            appendLine("}")
        }.toString()
    }

    /** The lookups from a service interface to its manager, and the client factory. */
    fun renderManagerFunctions(services: List<NameDetails>): String {
        return StringBuilder().apply {
            appendGeneratedHeader()
            if (services.isNotEmpty()) {
                appendLine("package dev.kilua.rpc")
                appendLine()
                appendLine("import kotlin.reflect.KClass")
                appendLine("import kotlinx.serialization.modules.SerializersModule")
                appendLine()
                appendLine("@Suppress(\"UNCHECKED_CAST\")")
                appendLine("inline fun <reified T : Any> getServiceManager(): RpcServiceManager<T> = when (T::class) {")
                services.forEach {
                    appendLine("    ${it.packageName}.${it.interfaceName}::class -> ${it.packageName}.${it.managerName} as RpcServiceManager<T>")
                }
                appendLine("    else -> throw IllegalArgumentException(\"Unknown service \${T::class}\")")
                appendLine("}")
                appendLine()
                appendLine("fun getAllServiceManagers(): List<RpcServiceManager<*>> = listOf(")
                services.forEach {
                    appendLine("    ${it.packageName}.${it.managerName},")
                }
                appendLine(")")
                appendLine()
                appendLine("fun getServiceManagers(vararg kclass: KClass<*>): List<RpcServiceManager<*>> {")
                appendLine("    return kclass.map {")
                appendLine("        when (it) {")
                services.forEach {
                    appendLine("            ${it.packageName}.${it.interfaceName}::class -> ${it.packageName}.${it.managerName}")
                }
                appendLine("            else -> throw IllegalArgumentException(\"Unknown service \${it.simpleName}\")")
                appendLine("        }")
                appendLine("    }")
                appendLine("}")
                appendLine()
                appendLine("public expect inline fun <reified T : Any> getService(")
                appendLine("    baseUrl: String? = null,")
                appendLine("    serializersModules: List<SerializersModule>? = null")
                appendLine("): T")
            }
            appendLine()
        }.toString()
    }

    /** The platform half of the client factory, including the `actual` for the common expect. */
    fun renderClientFunctions(
        services: List<NameDetails>,
        requestClass: String,
        fileJvmName: String?
    ): String {
        val requestClassName = requestClassNameOf(requestClass)
        return StringBuilder().apply {
            appendGeneratedHeader()
            if (services.isNotEmpty()) {
                fileJvmName?.let { appendLine("@file:JvmName(\"$it\")") }
                appendLine("package dev.kilua.rpc")
                appendLine()
                appendLine("import $requestClass")
                appendLine("import kotlinx.serialization.modules.SerializersModule")
                appendLine()
                appendLine("inline fun <reified T : Any> getService(baseUrl: String? = null, serializersModules: List<SerializersModule>? = null, noinline requestFilter: (suspend $requestClassName.() -> Unit)? = null): T = when (T::class) {")
                services.forEach {
                    appendLine("    ${it.packageName}.${it.interfaceName}::class -> ${it.packageName}.${it.className}(baseUrl, serializersModules, requestFilter) as T")
                }
                appendLine("    else -> throw IllegalArgumentException(\"Unknown service \${T::class}\")")
                appendLine("}")
                appendLine()
                appendLine("actual inline fun <reified T : Any> getService(baseUrl: String?, serializersModules: List<SerializersModule>?): T = getService(baseUrl, serializersModules, null)")
            }
            appendLine()
        }.toString()
    }

    /** Registers the whole set of service exceptions for polymorphic deserialization. */
    fun renderExceptions(exceptions: List<ExceptionNameDetails>): String {
        return StringBuilder().apply {
            appendGeneratedHeader()
            appendLine("package dev.kilua.rpc")
            appendLine()
            if (exceptions.isNotEmpty()) {
                appendLine("import kotlinx.serialization.modules.SerializersModule")
                appendLine("import kotlinx.serialization.modules.polymorphic")
                appendLine("import kotlinx.serialization.modules.subclass")
                appendLine()
                appendLine("private var registered = false")
                appendLine()
                appendLine("fun registerRpcServiceExceptions() {")
                appendLine("    if (!registered) {")
                appendLine("        RpcSerialization.exceptionsSerializersModule = SerializersModule {")
                appendLine("            polymorphic(AbstractServiceException::class) {")
                exceptions.forEach {
                    appendLine("                subclass(${it.packageName}.${it.className}::class)")
                }
                appendLine("            }")
                appendLine("        }")
                appendLine("        registered = true")
                appendLine("    }")
                appendLine("}")
            } else {
                appendLine("fun registerRpcServiceExceptions() {}")
            }
            appendLine()
        }.toString()
    }

    /**
     * Emit the init block body: how every method of the interface is bound.
     *
     * A method that is streamed is bound through the channel overload, which already covers the
     * handler the convention declares alongside it, so the plain bind is not emitted for a name that
     * has one.
     */
    private fun StringBuilder.emitManagerBinds(info: InterfaceInfo) {
        val channelNames = info.functions.filter { it.kind.isChannel }.map { it.name }.toSet()
        info.functions.forEach { function ->
            val name = function.name
            val route = function.route?.let { "\"$it\"" }
            when (function.kind) {
                RpcMethodKind.REMOTE_DATA ->
                    appendLine("        bindRemoteData(${info.interfaceName}::$name, $route)")

                RpcMethodKind.WEBSOCKET, RpcMethodKind.SSE ->
                    // The cast keeps overload resolution on the two argument bind rather than the
                    // three argument one.
                    appendLine("        bind(${info.interfaceName}::$name, ${route ?: "null as String?"})")

                RpcMethodKind.CALL ->
                    if (name !in channelNames)
                        appendLine("        bind(${info.interfaceName}::$name, ${function.httpMethod}, $route)")
            }
        }
    }

    private fun StringBuilder.appendGeneratedHeader() {
        appendLine("//")
        appendLine("// GENERATED by Kilua RPC")
        appendLine("//")
    }

    /**
     * Emit the imports a generated file needs for every type reachable from [types].
     *
     * A channel method names only one channel in its parameter list, but its client override flips
     * the direction and so names the other one too. Without this, an interface whose only streaming
     * method is SSE would generate an override referring to an unimported ReceiveChannel.
     */
    private fun StringBuilder.appendImports(types: Set<String>) {
        val imported = if (CHANNEL_SEND in types || CHANNEL_RECEIVE in types) {
            types + CHANNEL_SEND + CHANNEL_RECEIVE
        } else {
            types
        }
        imported.sorted().forEach { appendLine("import $it") }
    }

    /**
     * The simple name of the class a request filter parameter is declared in.
     *
     * Generated code refers to it by simple name only, having imported it above.
     */
    private fun requestClassNameOf(requestClass: String): String = requestClass.substringAfterLast('.')

    private fun parameterList(function: FunctionInfo): String =
        function.parameters.joinToString(", ") { "${it.name}: ${it.type}" }

    private fun parameterNames(function: FunctionInfo): String =
        function.parameters.joinToString(", ") { it.name }

    private const val CHANNEL_SEND = "kotlinx.coroutines.channels.SendChannel"
    private const val CHANNEL_RECEIVE = "kotlinx.coroutines.channels.ReceiveChannel"
}
